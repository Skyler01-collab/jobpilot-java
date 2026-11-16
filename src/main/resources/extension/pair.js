// Runs on the JobPilot page (127.0.0.1 / localhost). Lets the Settings page connect the extension in one click.
document.documentElement.dataset.jobpilotExt = '1';

window.addEventListener('message', e => {
  if (e.source !== window || !e.data || e.data.source !== 'jobpilot-ui' || e.data.type !== 'pair') return;
  chrome.runtime.sendMessage({ type: 'pair', token: e.data.token }, r => {
    const err = chrome.runtime.lastError ? chrome.runtime.lastError.message : (r && !r.ok ? r.error : '');
    window.postMessage({ source: 'jobpilot-ext', type: 'paired', ok: !err, error: err }, window.location.origin);
  });
});
