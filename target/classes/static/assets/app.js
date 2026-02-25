// State Management
const state = {
  token: localStorage.getItem('chat_token'),
  me: JSON.parse(localStorage.getItem('chat_user') || 'null'),
  ws: null,
  currentFriend: null,
  conversationId: null,
  authMode: 'login', // 'login', 'register', 'forgot'
  reconnectAttempts: 0,
  pendingRequestTimer: null,
  theme: localStorage.getItem('chat_theme') || 'dark',
  settings: {
    soundEnabled: localStorage.getItem('chat_sound_enabled') !== 'false',
    dndEnabled: localStorage.getItem('chat_dnd_enabled') === 'true'
  }
};

const SoundManager = {
  ctx: null,
  init() {
    try {
      window.AudioContext = window.AudioContext || window.webkitAudioContext;
      this.ctx = new AudioContext();
    } catch(e) { console.error("Web Audio API not supported"); }
  },
  play(force = false) {
    if (!force && !state.settings.soundEnabled) return;
    if (!force && this.isDndActive()) return;
    if (!this.ctx) this.init();
    if (!this.ctx) return;
    if (this.ctx.state === 'suspended') this.ctx.resume();
    
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();
    osc.connect(gain);
    gain.connect(this.ctx.destination);
    
    osc.type = 'sine'; 
    osc.frequency.setValueAtTime(880, this.ctx.currentTime); 
    osc.frequency.exponentialRampToValueAtTime(440, this.ctx.currentTime + 0.2);
    gain.gain.setValueAtTime(0.1, this.ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.01, this.ctx.currentTime + 0.2);
    osc.start();
    osc.stop(this.ctx.currentTime + 0.2);
  },
  isDndActive() {
    if (!state.settings.dndEnabled) return false;
    const hour = new Date().getHours();
    return hour >= 23 || hour < 7;
  }
};

// DOM Elements
const $ = id => document.getElementById(id);
const els = {
  authScreen: $('auth-screen'),
  chatScreen: $('chat-screen'),
  sidebar: $('sidebar'),
  chatArea: $('chat-area'),
  username: $('username'),
  usernameField: $('username-field'),
  password: $('password'),
  passwordField: $('password-field'),
  displayName: $('displayName'),
  email: $('email'),
  verifyCode: $('verifyCode'),
  sendCodeBtn: $('sendCodeBtn'),
  registerFields: $('register-fields'),
  emailField: $('email-field'),
  codeField: $('code-field'),
  newPassword: $('newPassword'),
  newPasswordField: $('new-password-field'),
  authError: $('authError'),
  mainBtn: $('mainBtn'),
  switchBtn: $('switchBtn'),
  forgotBtn: $('forgotBtn'),
  friendList: $('friendList'),
  messages: $('messages'),
  msgInput: $('msgInput'),
  currentChatName: $('currentChatName'),
  typingIndicator: $('typingIndicator'),
  fileInput: $('fileInput'),
  meName: $('meName'),
  meId: $('meId'),
  onlineCount: $('onlineCount'),
  myAvatar: $('my-avatar'),
  addFriendBtn: $('addFriendBtn'),
  addFriendModal: $('addFriendModal'),
  addFriendClose: $('addFriendClose'),
  addFriendAccount: $('addFriendAccount'),
  addFriendSuggestions: $('addFriendSuggestions'),
  addFriendHint: $('addFriendHint'),
  addFriendStatus: $('addFriendStatus'),
  addFriendCancel: $('addFriendCancel'),
  addFriendSend: $('addFriendSend'),
  addFriendWithdraw: $('addFriendWithdraw'),
  publicChannelBtn: $('publicChannelBtn'),
  openFriendRequests: $('openFriendRequests'),
  friendRequestModal: $('friendRequestModal'),
  friendRequestClose: $('friendRequestClose'),
  friendRequestRefresh: $('friendRequestRefresh'),
  friendRequestList: $('friendRequestList'),
  friendRequestBadge: $('friendRequestBadge'),
  // Settings
  openSettings: $('openSettings'),
  settingsModal: $('settingsModal'),
  settingsClose: $('settingsClose'),
  settingDisplayName: $('settingDisplayName'),
  saveDisplayNameBtn: $('saveDisplayNameBtn'),
  settingSoundEnabled: $('settingSoundEnabled'),
  settingDndEnabled: $('settingDndEnabled'),
  testSoundBtn: $('testSoundBtn'),
  // Avatar
  settingAvatarPreview: $('settingAvatarPreview'),
  avatarInput: $('avatarInput'),
  uploadAvatarBtn: $('uploadAvatarBtn'),
  uploadProgress: $('uploadProgress'),
  avatarHistory: $('avatarHistory'),
  avatarHistoryList: $('avatarHistoryList')
};

const addFriendState = { pendingAccount: null, searchTimer: null };

function init() {
  initSettings();
  setupEventListeners();
  if (state.token && state.me) {
    enterChat();
  } else {
    showAuth();
  }
}

function initSettings() {
  if (els.settingSoundEnabled) els.settingSoundEnabled.checked = state.settings.soundEnabled;
  if (els.settingDndEnabled) els.settingDndEnabled.checked = state.settings.dndEnabled;
}

function openSettingsModal() {
  if (els.settingsModal) {
    els.settingsModal.classList.remove('hidden');
    initSettings();
    loadAvatarSettings();
  }
}

function closeSettingsModal() {
  if (els.settingsModal) els.settingsModal.classList.add('hidden');
}

function setupEventListeners() {
  els.switchBtn.onclick = () => {
    if (state.authMode === 'login') {
      setAuthMode('register');
    } else {
      setAuthMode('login');
    }
  };
  if (els.forgotBtn) {
    els.forgotBtn.onclick = () => setAuthMode('forgot');
  }
  els.mainBtn.onclick = handleAuth;
  els.sendCodeBtn.onclick = handleSendCode;
  $('logoutBtn').onclick = () => handleLogout();
  
  els.msgInput.onkeydown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      if (e.isComposing || e.keyCode === 229) {
        return;
      }
      e.preventDefault();
      sendMessage();
    }
  };
  const handleTyping = throttle(() => {
    if (state.ws && state.currentFriend) {
      state.ws.send(JSON.stringify({ type: "typing", data: { toUserId: state.currentFriend } }));
    }
  }, 2000);

  els.msgInput.oninput = (e) => {
    // Auto-resize
    e.target.style.height = 'auto';
    e.target.style.height = e.target.scrollHeight + 'px';
    
    handleTyping();
  };
  
  $('sendBtn').onclick = sendMessage;
  $('fileBtn').onclick = () => els.fileInput.click();
  els.fileInput.onchange = handleFileUpload;
  $('emojiBtn').onclick = () => { els.msgInput.value += "😊"; els.msgInput.focus(); };
  $('backBtn').onclick = () => {
    els.sidebar.classList.remove('hidden');
    els.chatArea.classList.remove('active');
    state.currentFriend = null;
    state.currentGroupId = null;
    if (els.publicChannelBtn) els.publicChannelBtn.classList.remove('active');
  };
  
  if (els.addFriendBtn) {
    els.addFriendBtn.onclick = openAddFriendModal;
    els.addFriendClose.onclick = closeAddFriendModal;
    els.addFriendCancel.onclick = closeAddFriendModal;
    els.addFriendSend.onclick = handleSendFriendRequest;
    els.addFriendWithdraw.onclick = handleWithdrawFriendRequest;
    els.addFriendAccount.oninput = handleAddFriendInput;
  }
  
  if (els.publicChannelBtn) {
    els.publicChannelBtn.onclick = () => selectGroup(1);
  }
  
  if (els.openFriendRequests) els.openFriendRequests.onclick = openFriendRequestModal;
  if (els.friendRequestClose) els.friendRequestClose.onclick = closeFriendRequestModal;
  if (els.friendRequestRefresh) els.friendRequestRefresh.onclick = loadIncomingRequests;
  
  if (els.openSettings) els.openSettings.onclick = openSettingsModal;
  if (els.settingsClose) els.settingsClose.onclick = closeSettingsModal;
  if (els.saveDisplayNameBtn) els.saveDisplayNameBtn.onclick = handleUpdateProfile;
  
  if (els.settingSoundEnabled) els.settingSoundEnabled.onchange = (e) => {
    state.settings.soundEnabled = e.target.checked;
    localStorage.setItem('chat_sound_enabled', state.settings.soundEnabled);
  };
  if (els.settingDndEnabled) els.settingDndEnabled.onchange = (e) => {
    state.settings.dndEnabled = e.target.checked;
    localStorage.setItem('chat_dnd_enabled', state.settings.dndEnabled);
  };
  if (els.testSoundBtn) els.testSoundBtn.onclick = () => SoundManager.play(true);
  
  if (els.settingAvatarPreview) els.settingAvatarPreview.onclick = () => { if (els.avatarInput) els.avatarInput.click(); };
  if (els.avatarInput) els.avatarInput.onchange = handleAvatarSelect;
  if (els.uploadAvatarBtn) els.uploadAvatarBtn.onclick = handleAvatarUpload;
}

// API
async function api(path, method = "GET", body, options = {}) {
  try {
    const headers = {};
    if (!(body instanceof FormData)) headers["Content-Type"] = "application/json";
    if (state.token) headers["Authorization"] = "Bearer " + state.token;
    
    // CSRF Token
    const xsrf = getCookie('XSRF-TOKEN');
    if (xsrf) headers['X-XSRF-TOKEN'] = xsrf;
    
    const res = await fetch(path, {
      method,
      headers,
      body: (body && !(body instanceof FormData)) ? JSON.stringify(body) : body
    });
    
    if (!res.ok) {
      const text = await res.text();
      if ((res.status === 401 || res.status === 403) && !options.skipAuthRedirect) {
        handleLogout("登录已失效");
        return;
      }
      let message = text;
      try { message = JSON.parse(text).error || text; } catch (_) {}
      throw new Error(message);
    }
    return res.json();
  } catch (err) {
    console.error("API Error:", err);
    throw err;
  }
}

function getCookie(name) {
  if (!document.cookie) return null;
  const xsrfCookies = document.cookie.split(';')
    .map(c => c.trim())
    .filter(c => c.startsWith(name + '='));
  if (xsrfCookies.length === 0) return null;
  return decodeURIComponent(xsrfCookies[0].split('=')[1]);
}

function handleLogout(msg) {
  state.token = null;
  state.me = null;
  localStorage.removeItem('chat_token');
  localStorage.removeItem('chat_user');
  if (state.ws) state.ws.close();
  if (state.pendingRequestTimer) clearInterval(state.pendingRequestTimer);
  showAuth();
  if (msg) setAuthError(msg);
}

function showAuth() {
  setAuthError("");
  els.authScreen.classList.remove('hidden');
  els.chatScreen.classList.add('hidden');
}

function setAuthError(msg, type = 'error') {
  els.authError.textContent = msg;
  els.authError.classList.remove('hidden');
  els.authError.style.color = type === 'success' ? 'green' : 'red';
}

function setAuthMode(mode) {
  state.authMode = mode;
  setAuthError("");
  
  const isLogin = mode === 'login';
  const isRegister = mode === 'register';
  const isForgot = mode === 'forgot';

  // Toggle fields
  if (els.usernameField) els.usernameField.classList.toggle('hidden', !isLogin);
  if (els.passwordField) els.passwordField.classList.toggle('hidden', !isLogin && !isRegister);
  if (els.registerFields) els.registerFields.classList.toggle('hidden', !isRegister);
  if (els.emailField) els.emailField.classList.toggle('hidden', isLogin);
  if (els.codeField) els.codeField.classList.toggle('hidden', isLogin);
  if (els.newPasswordField) els.newPasswordField.classList.toggle('hidden', !isForgot);

  // Buttons
  if (isLogin) {
      els.mainBtn.textContent = '登录';
      els.switchBtn.textContent = '没有账号？去注册';
      if (els.forgotBtn) els.forgotBtn.classList.remove('hidden');
  } else if (isRegister) {
      els.mainBtn.textContent = '注册';
      els.switchBtn.textContent = '已有账号？去登录';
      if (els.forgotBtn) els.forgotBtn.classList.add('hidden');
  } else { // Forgot
      els.mainBtn.textContent = '重置密码';
      els.switchBtn.textContent = '返回登录';
      if (els.forgotBtn) els.forgotBtn.classList.add('hidden');
  }
}

async function handleAuth() {
  setAuthError("");
  
  try {
    if (state.authMode === 'login') {
      const account = els.username.value.trim();
      const password = els.password.value.trim();
      
      if (!account || !password) throw new Error("请输入账号和密码");
      
      const res = await api('/api/auth/login', 'POST', { account, password }, { skipAuthRedirect: true });
      if (!res) return;
      handleLoginSuccess(res);
      
    } else if (state.authMode === 'register') {
      const email = els.email.value.trim();
      const password = els.password.value.trim();
      const displayName = els.displayName.value.trim();
      const code = els.verifyCode.value.trim();
      
      if (!email || !password || !displayName || !code) throw new Error("请填写完整信息");
      
      // Verify code first
      await api('/api/auth/verify-email-code', 'POST', { email, code });
      
      // Register
      const res = await api('/api/auth/register', 'POST', { email, password, displayName });
      if (!res) return;
      handleLoginSuccess(res);
    } else if (state.authMode === 'forgot') {
      const email = els.email.value.trim();
      const code = els.verifyCode.value.trim();
      const newPassword = els.newPassword.value.trim();

      if (!email || !code || !newPassword) throw new Error("请填写完整信息");
      
      const res = await api('/api/auth/forgot-password', 'POST', { email, code, newPassword });
      if (!res) return;
      setAuthError(res.msg || "密码重置成功，请登录", "success");
      // Clear inputs
      els.verifyCode.value = "";
      els.newPassword.value = "";
      // Switch back to login after short delay or immediately
      setTimeout(() => setAuthMode('login'), 1500);
    }
  } catch (err) {
    setAuthError(err.message);
  }
}

function handleLoginSuccess(res) {
  state.token = res.token;
  state.me = res.user;
  localStorage.setItem('chat_token', state.token);
  localStorage.setItem('chat_user', JSON.stringify(state.me));
  
  els.authScreen.classList.add('hidden');
  enterChat();
}

async function handleSendCode() {
  const email = els.email.value.trim();
  if (!email) {
    setAuthError("请输入邮箱地址");
    return;
  }
  
  try {
    els.sendCodeBtn.disabled = true;
    let seconds = 60;
    const originalText = els.sendCodeBtn.textContent;
    
    await api('/api/auth/send-email-code', 'POST', { email });
    setAuthError("验证码已发送", "success");
    
    const timer = setInterval(() => {
      seconds--;
      els.sendCodeBtn.textContent = `${seconds}s后重试`;
      if (seconds <= 0) {
        clearInterval(timer);
        els.sendCodeBtn.disabled = false;
        els.sendCodeBtn.textContent = originalText;
      }
    }, 1000);
    
  } catch (err) {
    setAuthError(err.message);
    els.sendCodeBtn.disabled = false;
  }
}

async function enterChat() {
  els.authScreen.classList.add('hidden');
  els.chatScreen.classList.remove('hidden');
  els.meName.textContent = state.me.displayName;
  els.meId.textContent = `账号: ${state.me.username || ''}`;
  els.myAvatar.src = state.me.avatarUrl || `https://ui-avatars.com/api/?name=${state.me.displayName}&background=6366f1&color=fff`;
  
  await loadFriends();
  connectWS();
  loadIncomingRequests();
  if (!state.pendingRequestTimer) state.pendingRequestTimer = setInterval(loadIncomingRequests, 30000);
}

async function loadFriends() {
  try {
    const friends = await api("/api/friends");
    els.friendList.innerHTML = "";
    friends.forEach(f => {
      const div = document.createElement("div");
      div.className = "friend-item";
      div.innerHTML = `
        <img src="${f.avatarUrl || `https://ui-avatars.com/api/?name=${f.displayName}&background=random`}" class="avatar">
        <div class="flex-1 min-w-0">
          <div class="flex justify-between items-baseline">
            <div class="font-medium truncate">${f.remark || f.displayName}</div>
            <div class="text-xs text-secondary hidden">12:30</div>
          </div>
          <div class="text-sm text-secondary truncate">点击开始聊天</div>
        </div>
      `;
      div.onclick = () => selectFriend(f, div);
      els.friendList.appendChild(div);
    });
  } catch (e) { console.error(e); }
}

async function selectFriend(friend, el) {
  state.currentFriend = friend.friendId;
  state.currentGroupId = null;
  document.querySelectorAll('.friend-item').forEach(d => d.classList.remove('active'));
  els.publicChannelBtn.classList.remove('active');
  if (el) el.classList.add('active');
  
  if (window.innerWidth < 480) { // Changed from 768 to 480 to avoid hiding sidebar on tablets/small desktops
    els.sidebar.classList.add('hidden');
  }
  els.chatArea.classList.add('active');
  els.currentChatName.textContent = friend.remark || friend.displayName;
  
  await loadMessages();
}

async function selectGroup(groupId) {
  state.currentGroupId = groupId;
  state.currentFriend = null;
  document.querySelectorAll('.friend-item').forEach(d => d.classList.remove('active'));
  els.publicChannelBtn.classList.add('active');
  
  if (window.innerWidth < 480) {
    els.sidebar.classList.add('hidden');
  }
  els.chatArea.classList.add('active');
  els.currentChatName.textContent = "公共频道";
  
  await loadMessages();
}

async function loadMessages() {
  els.messages.innerHTML = '<div class="text-center text-secondary py-4">加载中...</div>';
  try {
    let url = "";
    if (state.currentGroupId) {
      url = `/api/messages?groupId=${state.currentGroupId}`;
    } else if (state.currentFriend) {
      url = `/api/messages?friendId=${state.currentFriend}`;
    } else {
      return;
    }
    const msgs = await api(url);
    els.messages.innerHTML = "";
    msgs.forEach(renderMessage);
    scrollToBottom();
  } catch (e) { els.messages.innerHTML = "加载失败"; }
}

function renderMessage(msg) {
  // Ensure strict comparison handles both string and number types correctly
  const isMe = String(msg.senderId) === String(state.me.id);

  // ACK/Duplicate Handling
  if (msg.id && document.querySelector(`.message[data-msg-id="${msg.id}"]`)) return;
  if (msg.tempId) {
    const tempEl = document.querySelector(`.message[data-temp-id="${msg.tempId}"]`);
    if (tempEl) {
      tempEl.setAttribute('data-msg-id', msg.id);
      tempEl.removeAttribute('data-temp-id');
      const spinner = tempEl.querySelector('.fa-spinner');
      if (spinner) spinner.remove();
      if (msg.timestamp || msg.createdAt) {
        const d = new Date(msg.timestamp || msg.createdAt);
        const t = d.toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute:'2-digit'});
        const meta = tempEl.querySelector('.message-meta');
        if (meta) meta.innerText = t; // Update time to server time
      }
      return;
    }
  }

  const div = document.createElement("div");
  div.className = `message ${isMe ? 'message-out' : 'message-in'}`;
  if (msg.id) div.setAttribute('data-msg-id', msg.id);
  if (msg.tempId) div.setAttribute('data-temp-id', msg.tempId);
  
  let senderHtml = "";
  if (!isMe && state.currentGroupId) {
      const avatar = msg.senderAvatar || `https://ui-avatars.com/api/?name=${msg.senderName || 'U'}&background=random&color=fff&size=64`;
      const name = msg.senderName || 'Unknown';
      senderHtml = `
      <div class="sender-info">
          <img src="${avatar}" class="sender-avatar">
          <span class="sender-name">${name}</span>
      </div>`;
  }

  let contentHtml = "";
  if (msg.contentType === 'image') {
    contentHtml = `<img src="${msg.content}" class="max-w-[200px] rounded-lg cursor-pointer" onclick="window.open(this.src)" loading="lazy">`;
  } else {
    // SECURITY: Sanitize content before rendering
    contentHtml = `<div class="bubble">${escapeHtml(msg.content)}</div>`;
  }
  
  // Ensure date is parsed in local time zone, but handle UTC string from backend
  const date = new Date(msg.createdAt);
  
  // If the backend returns UTC time (e.g. ends with 'Z' or no timezone), new Date() parses it as UTC.
  // But if the backend returns a local time string without timezone (e.g. "2024-02-24T12:00:00"), 
  // new Date() might interpret it as local time directly.
  // Given we are in China (UTC+8), let's ensure we are comparing dates correctly in local time.
  
  const now = new Date();
  let timeStr;
  
  // Helper to get date string in local time (YYYY-MM-DD) for comparison
  const toLocalDateString = (d) => {
    return d.getFullYear() + '-' + (d.getMonth() + 1) + '-' + d.getDate();
  };
  
  const dateStr = toLocalDateString(date);
  const todayStr = toLocalDateString(now);
  
  const isToday = dateStr === todayStr;
  
  // Create a clone of 'now' for yesterday check
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday = toLocalDateString(yesterday) === dateStr;
  
  // Check if same week (assuming week starts on Monday)
  const currentDay = now.getDay() || 7; // 1 (Mon) - 7 (Sun)
  const currentWeekMonday = new Date(now);
  currentWeekMonday.setDate(now.getDate() - currentDay + 1);
  currentWeekMonday.setHours(0, 0, 0, 0);
  
  // End of this week (Next Monday)
  const nextWeekMonday = new Date(currentWeekMonday);
  nextWeekMonday.setDate(currentWeekMonday.getDate() + 7);
  
  // For week comparison, check if date is within [currentWeekMonday, nextWeekMonday)
  const isSameWeek = date >= currentWeekMonday && date < nextWeekMonday;
  const isSameYear = date.getFullYear() === now.getFullYear();
 
  // Format time part: HH:mm (24-hour format)
  const timePart = date.toLocaleTimeString('zh-CN', {hour12: false, hour: '2-digit', minute:'2-digit'});
  
  if (isToday) {
    timeStr = timePart;
  } else if (isYesterday) {
    timeStr = "昨天 " + timePart;
  } else if (isSameWeek) {
    // If it is same week but NOT today and NOT yesterday
    const weekDays = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];
    timeStr = weekDays[date.getDay()] + " " + timePart;
  } else if (isSameYear) {
    timeStr = (date.getMonth() + 1) + "/" + date.getDate() + " " + timePart;
  } else {
    timeStr = date.getFullYear() + "/" + (date.getMonth() + 1) + "/" + date.getDate() + " " + timePart;
  }
  
  div.innerHTML = senderHtml + contentHtml + `<div class="message-meta">${timeStr}${msg.isTemp ? ' <i class="fas fa-spinner fa-spin text-xs"></i>' : ''}</div>`;
  els.messages.appendChild(div);
}

// Utility: Escape HTML to prevent XSS
function escapeHtml(unsafe) {
    if (!unsafe) return "";
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}

function scrollToBottom() { els.messages.scrollTop = els.messages.scrollHeight; }

async function sendMessage() {
  const content = els.msgInput.value.trim();
  if (!content || !state.ws) return;
  if (content.length > 1000) return showToast("消息过长，请控制在1000字以内", "warning");
  if (!state.currentFriend && !state.currentGroupId) return;
  
  const tempId = crypto.randomUUID();

  const data = { content, contentType: "text", tempId: tempId };
  if (state.currentGroupId) {
    data.groupId = state.currentGroupId;
  } else {
    data.receiverId = state.currentFriend;
  }

  renderMessage({ ...data, senderId: state.me.id, createdAt: new Date(), isTemp: true, tempId: tempId });
  scrollToBottom();

  state.ws.send(JSON.stringify({
    type: "send",
    data: data
  }));
  els.msgInput.value = "";
  els.msgInput.style.height = 'auto';
}

async function handleFileUpload(e) {
  const file = e.target.files[0];
  if (!file) return;
  
  // File size validation (e.g. 5MB)
  if (file.size > 5 * 1024 * 1024) {
      showToast("文件大小不能超过5MB", "warning");
      return;
  }
  
  try {
    showToast("正在上传...", "info");
    const formData = new FormData();
    formData.append("file", file);
    const res = await api("/api/files", "POST", formData);
    
    if (state.ws && (state.currentFriend || state.currentGroupId)) {
      const payload = {
          content: res.url,
          contentType: file.type.startsWith("image/") ? "image" : "file"
      };
      
      if (state.currentGroupId) {
          payload.groupId = state.currentGroupId;
      } else {
          payload.receiverId = state.currentFriend;
      }
      
      state.ws.send(JSON.stringify({
        type: "send",
        data: payload
      }));
      showToast("发送成功", "success");
    }
  } catch (err) { 
      console.error(err);
      showToast("发送文件失败", "error"); 
  }
}

// Toast Notification System
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return alert(message); // Fallback

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    let icon = 'info-circle';
    if (type === 'success') icon = 'check-circle';
    if (type === 'error') icon = 'exclamation-circle';
    if (type === 'warning') icon = 'exclamation-triangle';

    toast.innerHTML = `
        <i class="fas fa-${icon} toast-icon"></i>
        <div class="toast-content">${escapeHtml(message)}</div>
        <button class="toast-close" onclick="this.parentElement.remove()">
            <i class="fas fa-times"></i>
        </button>
    `;

    container.appendChild(toast);

    // Auto remove after 3 seconds
    setTimeout(() => {
        toast.style.animation = 'fadeOut 0.3s forwards';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

function connectWS() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  state.ws = new WebSocket(`${protocol}//${window.location.host}/ws?token=${state.token}`);
  state.ws.onopen = () => { state.reconnectAttempts = 0; console.log("WS Connected"); };
  state.ws.onmessage = (e) => {
    const msg = JSON.parse(e.data);
    if (msg.type === 'ack') {
      renderMessage(msg.data);
    } else if (msg.type === 'message') {
      const data = msg.data;
      // Convert to string for safe comparison
      const senderId = String(data.senderId);
      const currentFriendId = String(state.currentFriend);
      const currentGroupId = String(state.currentGroupId);
      const myId = String(state.me.id);
      
      if (state.currentGroupId && String(data.groupId) === currentGroupId) {
        renderMessage({ ...data, createdAt: new Date() });
        scrollToBottom();
        SoundManager.play();
      } else if (state.currentFriend && (senderId === currentFriendId || (senderId === myId && !data.groupId))) {
        renderMessage({ ...data, createdAt: new Date() });
        scrollToBottom();
        SoundManager.play();
      }
    } else if (msg.type === 'typing') {
      if (state.currentFriend === msg.data.senderId) {
        els.typingIndicator.classList.remove('hidden');
        setTimeout(() => els.typingIndicator.classList.add('hidden'), 3000);
      }
    } else if (msg.type === 'online_count') {
      if (els.onlineCount) els.onlineCount.textContent = msg.data.count;
    }
  };
  state.ws.onclose = () => {
    console.log("WS Closed");
    if (state.token) setTimeout(connectWS, Math.min(30000, 1000 * (2 ** state.reconnectAttempts++)));
  };
}

// Friend Requests
async function loadIncomingRequests() {
  try {
    const res = await api("/api/friends/requests/incoming");
    const list = res.items || [];
    els.friendRequestBadge.classList.toggle('hidden', list.length === 0);
    els.friendRequestBadge.textContent = list.length;
    
    if (els.friendRequestList) {
      els.friendRequestList.innerHTML = "";
      if (list.length === 0) {
        els.friendRequestList.innerHTML = '<div class="text-center text-secondary py-4">暂无好友请求</div>';
        return;
      }
      list.forEach(req => {
        const div = document.createElement("div");
        div.className = "flex items-center justify-between p-3 bg-gray-50 rounded-lg mb-2";
        div.innerHTML = `
          <div>
            <div class="font-medium">${req.displayName || req.account}</div>
            <div class="text-xs text-secondary">请求添加好友</div>
          </div>
          <div class="flex gap-2">
            <button class="btn btn-sm btn-primary" onclick="acceptRequest(${req.requesterId})">接受</button>
            <button class="btn btn-sm bg-gray-200" onclick="rejectRequest(${req.requesterId})">拒绝</button>
          </div>
        `;
        els.friendRequestList.appendChild(div);
      });
    }
  } catch (e) { console.error(e); }
}

async function acceptRequest(id) {
  try {
    await api("/api/friends/request/accept", "POST", { requesterId: id });
    loadIncomingRequests();
    loadFriends();
  } catch (e) { alert(e.message); }
}

async function rejectRequest(id) {
  try {
    await api("/api/friends/request/reject", "POST", { requesterId: id });
    loadIncomingRequests();
  } catch (e) { alert(e.message); }
}

function openFriendRequestModal() { els.friendRequestModal.classList.remove('hidden'); loadIncomingRequests(); }
function closeFriendRequestModal() { els.friendRequestModal.classList.add('hidden'); }
function openAddFriendModal() { resetAddFriendModal(); els.addFriendModal.classList.remove('hidden'); els.addFriendAccount.focus(); }
function closeAddFriendModal() { els.addFriendModal.classList.add('hidden'); }
function resetAddFriendModal() { els.addFriendAccount.value = ""; els.addFriendSuggestions.classList.add('hidden'); setAddFriendHint(""); setAddFriendStatus(""); els.addFriendSend.disabled = true; els.addFriendWithdraw.classList.add('hidden'); addFriendState.pendingAccount = null; }
function setAddFriendHint(t) { els.addFriendHint.textContent = t; }
function setAddFriendStatus(t, type) { els.addFriendStatus.textContent = t || ""; els.addFriendStatus.className = `status-text ${type||""} ${t?"":"hidden"}`; }
function handleAddFriendInput() {
  // Allow digits and common email characters
  const v = els.addFriendAccount.value.replace(/[^a-zA-Z0-9@._-]/g, "");
  els.addFriendAccount.value = v;
  // If it looks like a 10-digit ID or an email, trigger search
  if (v.length === 10 || v.includes('@')) searchAccountSuggestions(v);
}
async function searchAccountSuggestions(acc) {
  // Simple check for now
  if (acc === state.me.username) { setAddFriendStatus("不能添加自己", "error"); return; }
  els.addFriendSend.disabled = false;
  setAddFriendStatus("可发送请求", "success");
}
async function handleSendFriendRequest() {
  const account = els.addFriendAccount.value;
  try {
    await api("/api/friends/request", "POST", { account });
    setAddFriendStatus("请求已发送", "success");
    els.addFriendSend.disabled = true;
    addFriendState.pendingAccount = account;
    els.addFriendWithdraw.classList.remove('hidden');
  } catch (e) { setAddFriendStatus(e.message, "error"); }
}
async function handleWithdrawFriendRequest() {
  const account = addFriendState.pendingAccount;
  if (!account) return;
  try {
    await api(`/api/friends/request?account=${account}`, "DELETE");
    setAddFriendStatus("已取消", "success");
    els.addFriendWithdraw.classList.add('hidden');
  } catch (e) { setAddFriendStatus(e.message, "error"); }
}

async function handleUpdateProfile() {
  const displayName = els.settingDisplayName.value.trim();
  if (!displayName) return alert("昵称不能为空");
  try {
    const res = await api("/api/user/profile", "POST", { displayName });
    state.me.displayName = res.displayName;
    localStorage.setItem('chat_user', JSON.stringify(state.me));
    els.meName.textContent = state.me.displayName;
    alert("昵称已更新");
  } catch (e) {
    alert("更新失败: " + e.message);
  }
}

// Avatar Settings
let avatarFile = null;
function loadAvatarSettings() {
  if (els.settingAvatarPreview) els.settingAvatarPreview.src = state.me?.avatarUrl || `https://ui-avatars.com/api/?name=${state.me?.displayName || 'U'}&background=6366f1&color=fff`;
  if (els.settingDisplayName) els.settingDisplayName.value = state.me?.displayName || "";
  avatarFile = null;
  if (els.uploadAvatarBtn) {
    els.uploadAvatarBtn.disabled = true;
    els.uploadAvatarBtn.textContent = "上传并保存";
  }
  if (els.uploadProgress) els.uploadProgress.classList.add('hidden');
  loadAvatarHistory();
}
function handleAvatarSelect(e) {
  const file = e.target.files[0];
  if (!file) return;
  if (file.size > 5 * 1024 * 1024) return alert("文件大小不能超过5MB");
  if (!['image/jpeg', 'image/png'].includes(file.type)) return alert("仅支持 JPG 或 PNG");
  avatarFile = file;
  const reader = new FileReader();
  reader.onload = (e) => {
    if (els.settingAvatarPreview) els.settingAvatarPreview.src = e.target.result;
  };
  reader.readAsDataURL(file);
  if (els.uploadAvatarBtn) els.uploadAvatarBtn.disabled = false;
}
async function handleAvatarUpload() {
  if (!avatarFile) return;
  try {
    if (els.uploadAvatarBtn) {
      els.uploadAvatarBtn.disabled = true;
      els.uploadAvatarBtn.textContent = "上传中...";
    }
    if (els.uploadProgress) els.uploadProgress.classList.remove('hidden');
    const formData = new FormData();
    formData.append('file', avatarFile);
    const res = await api('/api/user/avatar', 'POST', formData);
    
    state.me.avatarUrl = res.avatarUrl;
    localStorage.setItem('chat_user', JSON.stringify(state.me));
    if (els.myAvatar) els.myAvatar.src = res.avatarUrl;
    if (els.settingAvatarPreview) els.settingAvatarPreview.src = res.avatarUrl;
    
    if (els.uploadAvatarBtn) els.uploadAvatarBtn.textContent = "上传成功";
    setTimeout(() => {
      if (els.uploadAvatarBtn) {
        els.uploadAvatarBtn.textContent = "上传并保存";
        els.uploadAvatarBtn.disabled = true;
      }
      avatarFile = null;
      if (els.uploadProgress) els.uploadProgress.classList.add('hidden');
      loadAvatarHistory();
    }, 1500);
  } catch (e) {
    if (els.uploadAvatarBtn) {
      els.uploadAvatarBtn.textContent = "上传失败";
      els.uploadAvatarBtn.disabled = false;
    }
    alert("上传失败: " + e.message);
  }
}
async function loadAvatarHistory() {
  if (!els.avatarHistory || !els.avatarHistoryList) return;
  try {
    const history = await api('/api/user/avatar/history');
    if (!history || history.length === 0) {
      els.avatarHistory.classList.add('hidden');
      return;
    }
    els.avatarHistory.classList.remove('hidden');
    els.avatarHistoryList.innerHTML = '';
    history.forEach(item => {
      const img = document.createElement('img');
      img.src = item.url;
      img.className = 'avatar w-10 h-10 cursor-pointer object-cover border-2 border-transparent hover:border-indigo-500';
      img.title = '点击回滚到此头像';
      img.onclick = () => handleRollbackAvatar(item.key, item.url);
      els.avatarHistoryList.appendChild(img);
    });
  } catch (e) { console.error(e); }
}
async function handleRollbackAvatar(key, url) {
  if (!confirm("确定要回滚到此头像吗？")) return;
  try {
    await api('/api/user/avatar/rollback', 'POST', { key });
    state.me.avatarUrl = url;
    localStorage.setItem('chat_user', JSON.stringify(state.me));
    els.myAvatar.src = url;
    els.settingAvatarPreview.src = url;
    alert("头像已回滚");
  } catch (e) { alert("回滚失败: " + e.message); }
}

function throttle(func, limit) {
  let inThrottle;
  return function() {
    const args = arguments, context = this;
    if (!inThrottle) {
      func.apply(context, args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  }
}

// Start
init();