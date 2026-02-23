(function () {
  const defaultConfig = {
    endpoint: "/api/errors/collect",
    appVersion: "unknown",
    userGetter: null,
    onFatal: null
  };

  let config = { ...defaultConfig };
  let context = { module: "app", component: "", route: "" };
  let lastError = null;
  let reporting = false;
  let lastShownAt = 0;

  function nowIso() {
    return new Date().toISOString();
  }

  function maskSensitive(text) {
    if (!text) return text;
    let out = String(text);
    out = out.replace(/Bearer\s+[A-Za-z0-9\-\._~\+\/]+=*/gi, "Bearer [REDACTED]");
    out = out.replace(/"password"\s*:\s*"[^"]*"/gi, "\"password\":\"[REDACTED]\"");
    out = out.replace(/password=([^&\s]+)/gi, "password=[REDACTED]");
    out = out.replace(/\b\d{10}\b/g, "**********");
    return out;
  }

  function buildUserContext() {
    const user = typeof config.userGetter === "function" ? config.userGetter() : null;
    if (!user) return { userId: null, username: null };
    return { userId: user.id || null, username: user.username || null };
  }

  function classify(error, meta) {
    const type = meta?.type || "script";
    let severity = meta?.severity || "error";
    if (type === "resource") severity = "error";
    if (type === "network") severity = "warn";
    if (type === "business") severity = "info";
    if (type === "promise") severity = "error";
    return { type, severity };
  }

  function formatPayload(error, meta) {
    const { userId, username } = buildUserContext();
    const err = error instanceof Error ? error : new Error(String(error || "Unknown error"));
    const message = maskSensitive(err.message || meta?.message || "Unknown error");
    const stack = maskSensitive(err.stack || meta?.stack || "");
    const url = maskSensitive(window.location.href);
    const ua = maskSensitive(navigator.userAgent);
    const resourceUrl = maskSensitive(meta?.resourceUrl || "");
    const { type, severity } = classify(err, meta);
    const payload = {
      type,
      severity,
      message,
      stack,
      url,
      userAgent: ua,
      component: maskSensitive(meta?.component || context.component || ""),
      module: maskSensitive(meta?.module || context.module || ""),
      route: maskSensitive(meta?.route || context.route || ""),
      userId,
      username,
      resourceUrl,
      statusCode: meta?.statusCode || null,
      requestMethod: meta?.requestMethod || null,
      version: config.appVersion || "unknown",
      time: nowIso(),
      extra: meta?.extra || null
    };
    return payload;
  }

  function send(payload) {
    if (!payload || reporting) return;
    reporting = true;
    const body = JSON.stringify(payload);
    if (navigator.sendBeacon) {
      try {
        navigator.sendBeacon(config.endpoint, body);
        reporting = false;
        return;
      } catch (_) {
      }
    }
    fetch(config.endpoint, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
      keepalive: true
    }).finally(() => {
      reporting = false;
    });
  }

  function showBanner(payload) {
    const banner = document.getElementById("globalErrorBanner");
    if (!banner) return;
    const msg = banner.querySelector("[data-error-message]");
    const details = banner.querySelector("[data-error-details]");
    if (msg) msg.textContent = payload.message || "发生未知错误";
    if (details) details.textContent = payload.type || "error";
    banner.classList.remove("hidden");
    lastShownAt = Date.now();
  }

  function maybeShowBanner(payload) {
    if (payload.severity === "error") {
      showBanner(payload);
      if (typeof config.onFatal === "function") {
        config.onFatal(payload);
      }
    }
  }

  function report(error, meta) {
    const payload = formatPayload(error, meta);
    lastError = payload;
    send(payload);
    maybeShowBanner(payload);
    return payload;
  }

  function handleWindowError(message, source, lineno, colno, error) {
    report(error || message, {
      type: "script",
      component: source,
      extra: { lineno, colno }
    });
  }

  function handleUnhandledRejection(event) {
    const reason = event?.reason || "Unhandled rejection";
    report(reason, { type: "promise" });
  }

  function handleResourceError(event) {
    const target = event.target || event.srcElement;
    if (!target) return;
    const tag = target.tagName ? target.tagName.toLowerCase() : "";
    if (!tag || tag === "body" || tag === "html") return;
    const resourceUrl = target.src || target.href || "";
    report("Resource load failed", { type: "resource", resourceUrl, component: tag });
  }

  function attachFrameworkHooks() {
    if (window.Vue && window.Vue.config) {
      const previous = window.Vue.config.errorHandler;
      window.Vue.config.errorHandler = function (err, vm, info) {
        report(err, { type: "framework", component: info, extra: { framework: "vue" } });
        if (typeof previous === "function") {
          previous.call(this, err, vm, info);
        }
      };
    }
  }

  function createReactErrorBoundary(React) {
    if (!React || !React.Component) return null;
    return class ErrorBoundary extends React.Component {
      constructor(props) {
        super(props);
        this.state = { hasError: false };
        this.handleReset = this.handleReset.bind(this);
      }
      static getDerivedStateFromError() {
        return { hasError: true };
      }
      componentDidCatch(err, info) {
        report(err, { type: "framework", component: info?.componentStack || "", extra: { framework: "react" } });
      }
      handleReset() {
        this.setState({ hasError: false });
      }
      render() {
        if (this.state.hasError) {
          if (this.props.fallback) {
            return typeof this.props.fallback === "function" ? this.props.fallback(this.handleReset) : this.props.fallback;
          }
          return null;
        }
        return this.props.children;
      }
    };
  }

  function setContext(next) {
    context = { ...context, ...next };
  }

  function wrap(fn, meta) {
    return function (...args) {
      try {
        return fn.apply(this, args);
      } catch (err) {
        report(err, meta);
        throw err;
      }
    };
  }

  function wrapAsync(fn, meta) {
    return async function (...args) {
      try {
        return await fn.apply(this, args);
      } catch (err) {
        report(err, meta);
        throw err;
      }
    };
  }

  function loadThirdPartyScript(url, options = {}) {
    return new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = url;
      script.async = true;
      script.crossOrigin = "anonymous";
      script.onload = () => resolve(url);
      script.onerror = () => {
        report("Third-party script failed", { type: "resource", resourceUrl: url });
        reject(new Error("Third-party script failed"));
      };
      if (options.integrity) script.integrity = options.integrity;
      if (options.referrerPolicy) script.referrerPolicy = options.referrerPolicy;
      document.head.appendChild(script);
    });
  }

  function init(options = {}) {
    config = { ...defaultConfig, ...options };
    window.onerror = handleWindowError;
    window.addEventListener("unhandledrejection", handleUnhandledRejection);
    window.addEventListener("error", handleResourceError, true);
    attachFrameworkHooks();
  }

  function getLastError() {
    return lastError;
  }

  function copyLastError() {
    if (!lastError) return false;
    const text = JSON.stringify(lastError, null, 2);
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text);
      return true;
    }
    return false;
  }

  function shouldShowHelp() {
    return Date.now() - lastShownAt < 5 * 60 * 1000;
  }

  window.ErrorMonitor = {
    init,
    report,
    setContext,
    wrap,
    wrapAsync,
    loadThirdPartyScript,
    createReactErrorBoundary,
    getLastError,
    copyLastError,
    shouldShowHelp
  };
})();
