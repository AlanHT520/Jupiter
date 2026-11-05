const BASE = '';

// HTML 转义：所有拼进 innerHTML 的服务端数据都应经过这里
function esc(value) {
    return String(value == null ? '' : value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Token management
function getToken() {
    return localStorage.getItem('jwt_token') || sessionStorage.getItem('jwt_token');
}

function setToken(token, remember) {
    if (remember) {
        localStorage.setItem('jwt_token', token);
    } else {
        sessionStorage.setItem('jwt_token', token);
    }
}

function clearToken() {
    localStorage.removeItem('jwt_token');
    sessionStorage.removeItem('jwt_token');
    localStorage.removeItem('user_info');
    sessionStorage.removeItem('user_info');
}

function getUserInfo() {
    var raw = localStorage.getItem('user_info') || sessionStorage.getItem('user_info');
    return raw ? JSON.parse(raw) : null;
}

function setUserInfo(info) {
    var raw = JSON.stringify(info);
    if (localStorage.getItem('jwt_token')) {
        localStorage.setItem('user_info', raw);
    } else {
        sessionStorage.setItem('user_info', raw);
    }
}

// API request
function api(method, url, data) {
    var headers = { 'Content-Type': 'application/json' };
    var token = getToken();
    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }
    var opts = {
        method: method,
        headers: headers
    };
    if (data && (method === 'POST' || method === 'PUT' || method === 'PATCH')) {
        opts.body = JSON.stringify(data);
    }
    return fetch(BASE + url, opts)
        .then(function(res) {
            if (res.status === 401) {
                clearToken();
                window.location.href = '/pe/index.html';
                throw new Error('未登录');
            }
            if (res.status === 403) {
                throw new Error('权限不足 (403)');
            }
            var ct = res.headers.get('content-type') || '';
            if (ct.indexOf('application/json') !== -1) {
                return res.json().then(function(json) {
                    if (json.code && json.code !== 200) {
                        throw new Error(json.message || '请求错误 (' + json.code + ')');
                    }
                    return json;
                });
            }
            return res.text().then(function(text) {
                throw new Error('服务器错误 (' + res.status + ')');
            });
        })
        .catch(function(err) {
            if (err instanceof TypeError && err.message.indexOf('fetch') !== -1) {
                throw new Error('网络连接失败，请确认服务器已启动');
            }
            throw err;
        });
}

function apiGet(url) { return api('GET', url); }
function apiPost(url, data) { return api('POST', url, data); }
function apiPut(url, data) { return api('PUT', url, data); }

// Toast
function showToast(msg, type) {
    var t = document.getElementById('toast');
    if (!t) {
        t = document.createElement('div');
        t.id = 'toast';
        t.className = 'toast';
        document.body.appendChild(t);
    }
    t.textContent = msg;
    t.className = 'toast ' + (type || 'success') + ' show';
    setTimeout(function() { t.className = 'toast'; }, 2000);
}

// Login check
function requireAuth() {
    var token = getToken();
    if (!token) {
        window.location.href = '/pe/index.html';
        return false;
    }
    return true;
}

// Logout
function logout() {
    clearToken();
    window.location.href = '/pe/index.html';
}

// Get URL param
function getParam(name) {
    var params = new URLSearchParams(window.location.search);
    return params.get(name);
}

// Check status labels
var STATUS_LABELS = {
    'PRESENT': { label: '在寝', css: 'present' },
    'ABSENT': { label: '不在', css: 'absent' },
    'PERSONAL_LEAVE': { label: '事假', css: 'personal' },
    'SICK_LEAVE': { label: '病假', css: 'sick' },
    'LONG_SICK_LEAVE': { label: '长期病假', css: 'sick' },
    'MILITARY': { label: '征兵', css: 'military' },
    'INTERN': { label: '实习', css: 'intern' },
    'DAY_STUDENT': { label: '走读', css: 'day' },
    'LATE_RETURN': { label: '晚归', css: 'absent' }
};

function statusLabel(status) {
    return STATUS_LABELS[status] ? STATUS_LABELS[status].label : status;
}

function statusCss(status) {
    return STATUS_LABELS[status] ? STATUS_LABELS[status].css : '';
}

// Format date
function fmtDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    var m = (d.getMonth() + 1).toString().padStart(2, '0');
    var day = d.getDate().toString().padStart(2, '0');
    return m + '-' + day;
}

// Format datetime
function fmtDateTime(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    var m = (d.getMonth() + 1).toString().padStart(2, '0');
    var day = d.getDate().toString().padStart(2, '0');
    var h = d.getHours().toString().padStart(2, '0');
    var min = d.getMinutes().toString().padStart(2, '0');
    return m + '-' + day + ' ' + h + ':' + min;
}