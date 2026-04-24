/**
 * logger.js — Frontend structured logger
 * MUST be loaded AFTER api.js and layout.js
 *
 * Usage:
 *   Logger.info('Login', 'Attempting login')
 *   Logger.error('API', 'Request failed', err)
 *
 * Tip: open browser DevTools console, then run Logger.dumpLogs() to see all logs.
 */

const Logger = (() => {
  const LEVELS = { DEBUG: 0, INFO: 1, WARN: 2, ERROR: 3 };
  const MIN_LEVEL = LEVELS.DEBUG;
  const MAX_BUFFER = 200;

  const STYLES = {
    DEBUG: 'color:#888;font-weight:normal',
    INFO:  'color:#1a3a5c;font-weight:600',
    WARN:  'color:#d4870a;font-weight:700',
    ERROR: 'color:#c8392b;font-weight:700',
  };

  let buffer = [];
  try {
    const stored = localStorage.getItem('rl_logs');
    if (stored) buffer = JSON.parse(stored).slice(-MAX_BUFFER);
  } catch (_) {}

  function write(level, module, message, data) {
    if (LEVELS[level] < MIN_LEVEL) return;
    const entry = { ts: new Date().toISOString(), level, module, message, data: data ?? null };

    const prefix = `%c[${entry.ts.slice(11,23)}] [${level}] [${module}]`;
    if (data !== undefined && data !== null) {
      console.groupCollapsed(prefix, STYLES[level], message);
      console.log(data);
      console.groupEnd();
    } else {
      console.log(prefix, STYLES[level], message);
    }

    buffer.push(entry);
    if (buffer.length > MAX_BUFFER) buffer.shift();

    if (LEVELS[level] >= LEVELS.WARN) {
      try {
        const persisted = buffer.filter(e => LEVELS[e.level] >= LEVELS.WARN).slice(-50);
        localStorage.setItem('rl_logs', JSON.stringify(persisted));
      } catch (_) {}
    }
  }

  return {
    debug: (m, msg, d) => write('DEBUG', m, msg, d),
    info:  (m, msg, d) => write('INFO',  m, msg, d),
    warn:  (m, msg, d) => write('WARN',  m, msg, d),
    error: (m, msg, d) => write('ERROR', m, msg, d),
    getLogs: () => [...buffer],
    clearLogs: () => { buffer = []; try { localStorage.removeItem('rl_logs'); } catch(_){} },
    dumpLogs: () => console.table(buffer.map(e => ({
      time: e.ts.slice(11,23), level: e.level, module: e.module, message: e.message
    }))),
  };
})();

// ── Patch window.fetch to log all API calls ───────────────────────
(function patchFetch() {
  const _fetch = window.fetch.bind(window);

  window.fetch = async function(url, options) {
    options = options || {};
    const method = (options.method || 'GET').toUpperCase();
    const shortUrl = String(url).replace('http://localhost:8080', '');

    // Safe body parse — never throw
    let bodyData;
    try {
      bodyData = options.body ? JSON.parse(options.body) : undefined;
    } catch (_) {
      bodyData = options.body ? '[non-JSON body]' : undefined;
    }

    Logger.debug('API', `→ ${method} ${shortUrl}`, bodyData);

    const start = Date.now();
    try {
      const response = await _fetch(url, options);
      const ms = Date.now() - start;

      if (response.ok) {
        Logger.info('API', `← ${method} ${shortUrl} [${response.status}] ${ms}ms`);
      } else {
        Logger.warn('API', `← ${method} ${shortUrl} [${response.status}] ${ms}ms`);
      }
      return response;

    } catch (err) {
      const ms = Date.now() - start;
      Logger.error('API', `✗ ${method} ${shortUrl} FAILED after ${ms}ms`, {
        message: err.message,
        hint: err.message === 'Failed to fetch'
          ? '1) Is the backend running? (mvn spring-boot:run)  2) Check CORS — opening file:// is OK now.'
          : 'Check DevTools Network tab for details.',
      });
      throw err;  // re-throw so caller error handling still works
    }
  };
})();

// ── Global error handlers ─────────────────────────────────────────
window.addEventListener('error', (e) => {
  Logger.error('GLOBAL', `Uncaught JS error: ${e.message}`, {
    file: e.filename, line: e.lineno, col: e.colno
  });
});

window.addEventListener('unhandledrejection', (e) => {
  Logger.error('GLOBAL', `Unhandled Promise rejection`, { reason: String(e.reason) });
});

// ── Startup banner ───────────────────────────────────────────────
Logger.info('INIT', 'Regulatory Platform loaded', {
  page: location.pathname.split('/').pop(),
  origin: location.origin,
  apiBase: 'http://localhost:8080/api',
});
