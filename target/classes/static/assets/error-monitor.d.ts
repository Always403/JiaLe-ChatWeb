export type ErrorMonitorSeverity = "info" | "warn" | "error";
export type ErrorMonitorType = "script" | "promise" | "network" | "business" | "resource" | "framework";

export interface ErrorMonitorPayload {
  type: ErrorMonitorType;
  severity: ErrorMonitorSeverity;
  message: string;
  stack?: string;
  url?: string;
  userAgent?: string;
  component?: string;
  module?: string;
  route?: string;
  userId?: number | null;
  username?: string | null;
  resourceUrl?: string;
  statusCode?: number | null;
  requestMethod?: string | null;
  version?: string;
  time?: string;
  extra?: Record<string, unknown> | null;
}

export interface ErrorMonitorConfig {
  endpoint?: string;
  appVersion?: string;
  userGetter?: () => { id?: number | null; username?: string | null } | null;
  onFatal?: (payload: ErrorMonitorPayload) => void;
}

export interface ErrorMonitorAPI {
  init: (config?: ErrorMonitorConfig) => void;
  report: (error: unknown, meta?: Partial<ErrorMonitorPayload>) => ErrorMonitorPayload;
  setContext: (context: Partial<Pick<ErrorMonitorPayload, "component" | "module" | "route">>) => void;
  wrap: <T extends (...args: any[]) => any>(fn: T, meta?: Partial<ErrorMonitorPayload>) => T;
  wrapAsync: <T extends (...args: any[]) => Promise<any>>(fn: T, meta?: Partial<ErrorMonitorPayload>) => T;
  loadThirdPartyScript: (url: string, options?: { integrity?: string; referrerPolicy?: string }) => Promise<string>;
  createReactErrorBoundary: (React: any) => any;
  getLastError: () => ErrorMonitorPayload | null;
  copyLastError: () => boolean;
  shouldShowHelp: () => boolean;
}

declare global {
  interface Window {
    ErrorMonitor: ErrorMonitorAPI;
  }
}

export {};
