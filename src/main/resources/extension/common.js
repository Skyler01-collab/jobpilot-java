// Shared by every page the extension touches: form filling, answer tracking and the floating panel.
(() => {
  if (window.__jp) return;
  const jp = (window.__jp = { aiFilled: new WeakSet(), filling: false, submitted: false, msg: '', server: 'http://127.0.0.1:8765' });

  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const norm = s => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  jp.esc = esc;

  jp.bg = (type, payload = {}) => new Promise(res => {
    try {
      chrome.runtime.sendMessage({ type, ...payload }, r => {
        if (chrome.runtime.lastError) res({ ok: false, error: chrome.runtime.lastError.message });
        else res(r || { ok: false, error: 'No response from the extension' });
      });
    } catch (e) { res({ ok: false, error: e.message }); }
  });
  jp.api = (path, method = 'GET', body) => jp.bg('api', { path, method, body });

  const visible = el => el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
  jp.visible = visible;

  /* ---------- reading field labels ---------- */
  function questionText(el) {
    const parts = [];
    if (el.labels && el.labels.length) parts.push([...el.labels].map(l => l.innerText).join(' '));
    const al = el.getAttribute('aria-label');
    if (al) parts.push(al);
    const by = el.getAttribute('aria-labelledby');
    if (by) parts.push(by.split(/\s+/).map(id => { const n = document.getElementById(id); return n ? n.innerText : ''; }).join(' '));
    let s = parts.join(' ').replace(/\s+/g, ' ').trim();
    if (!s) {
      let c = el.parentElement;
      for (let i = 0; i < 3 && c && !s; i++, c = c.parentElement) {
        const l = c.querySelector('label, legend, [class*="label"], [class*="question"]');
        if (l && !l.contains(el)) s = (l.innerText || '').replace(/\s+/g, ' ').trim();
      }
    }
    if (!s && el.placeholder) s = el.placeholder;
    return s.replace(/[*:]+\s*$/, '').replace(/\s*\(?required\)?\s*$/i, '').slice(0, 200).trim();
  }

  /* ---------- what the profile can answer ---------- */
  const RULES = [
    { re: /first.?name|given.?name|fname/i, get: p => (p.fullName || '').trim().split(/\s+/)[0] },
    { re: /last.?name|sur.?name|family.?name|lname/i, get: p => { const t = (p.fullName || '').trim().split(/\s+/); return t.length > 1 ? t[t.length - 1] : ''; } },
    { re: /full.?name|^name\b|your.?name|candidate.?name/i, get: p => p.fullName },
    { re: /e-?mail/i, get: p => p.email },
    { re: /phone|mobile|contact.?number|\btel\b/i, get: p => p.phone },
    { re: /linkedin/i, get: p => p.linkedin },
    { re: /github/i, get: p => p.github },
    { re: /total.*experience|years?.*experience|experience.*years?/i, get: p => (p.yearsExperience ? String(p.yearsExperience) : '') },
    { re: /current.?(job.?)?title|job.?title|designation|headline/i, get: p => p.headline || ((p.experience || [])[0] || {}).title },
    { re: /current.?(company|employer|organi[sz]ation)|^employer/i, get: p => ((p.experience || [])[0] || {}).company },
    { re: /\bcity\b|current.?location|location|address/i, get: p => p.location }
  ];
  const SENSITIVE = /password|passcode|\botp\b|captcha|aadhaar|aadhar|\bpan\b|passport|\bssn\b|social security|card number|cvv|bank|account number|date of birth|\bdob\b|gender|religion|caste|disab|ethnic|\brace\b|veteran|marital|sexual|pronoun|criminal|convict/i;
  const COVER = /cover.?letter|covering.?letter|letter of interest|why (do you want|are you interested|would you like)|tell us (why|about yourself)|additional information|message to (the )?(hiring|recruiter)/i;
  const SUBMITTED = /(application (has been |was )?(submitted|received|sent)|thank(s| you) for applying|you(?:'|’)?ve (successfully )?applied|successfully applied|your application (has been|was) (sent|submitted))/i;

  /* ---------- setting values ---------- */
  function setValue(el, v) {
    jp.filling = true;
    try {
      const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : el.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
      Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
    } finally { jp.filling = false; }
  }
  function mark(el, kind) {
    const green = kind === 'green';
    el.style.backgroundColor = green ? '#e3f6ee' : '#fff3d6';
    el.style.outline = '2px solid ' + (green ? '#0b6e5c' : '#b7791f');
    el.title = green ? 'Filled by JobPilot from your profile or saved answers' : 'Draft by JobPilot. Check it before you submit.';
    el.dataset.jpDone = '1';
  }

  /* ---------- the fill ---------- */
  async function doFill(opts) {
    const [pr, ar, sr, or] = await Promise.all([jp.api('/profile'), jp.api('/answers'), jp.api('/settings'), jp.bg('opts')]);
    if (!pr.ok) { jp.msg = pr.error; return { green: 0, amber: 0, files: 0, error: pr.error }; }
    const p = pr.data;
    const saved = ar.ok ? ar.data : [];
    const o = { ...(or.ok ? or.data : {}), ...opts };
    jp.server = o.server || jp.server;
    const aiOn = o.ai !== false && sr.ok && sr.data.aiConfigured;

    const findSaved = (q, exact) => {
      const n = norm(q);
      if (!n) return null;
      let hit = saved.find(a => a.qkey === n);
      if (!hit && !exact && n.length > 10) hit = saved.find(a => a.qkey.length > 10 && (n.includes(a.qkey) || a.qkey.includes(n)));
      return hit ? hit.answer : null;
    };

    let cover = null, pdf = null;
    const getCover = async () => {
      if (!opts.jobId) return '';
      if (cover === null) { const r = await jp.bg('cover', { jobId: opts.jobId }); cover = r.ok ? r.data : ''; }
      return cover;
    };
    const getPdf = async () => {
      if (!opts.jobId) return null;
      if (pdf === null) { const r = await jp.bg('pdf', { jobId: opts.jobId }); pdf = r.ok ? r.data : false; }
      return pdf || null;
    };

    const stats = { green: 0, amber: 0, files: 0 };
    let aiCalls = 0;

    for (const el of document.querySelectorAll('input, textarea, select')) {
      if (el.dataset.jpDone === '1' || el.disabled || el.readOnly) continue;
      const type = (el.type || '').toLowerCase();
      if (['hidden', 'password', 'checkbox', 'radio', 'submit', 'button', 'image', 'reset'].includes(type)) continue;

      // ----- resume upload -----
      if (type === 'file') {
        if (o.attachResume === false || !opts.jobId || (el.files && el.files.length)) continue;
        const h = questionText(el) + ' ' + (el.name || '') + ' ' + (el.id || '') + ' ' + (el.accept || '');
        if (/cover/i.test(h)) continue;
        if (!(/resume|\bcv\b|curriculum/i.test(h) || /pdf/i.test(el.accept || ''))) continue;
        const f = await getPdf();
        if (!f) continue;
        const bytes = Uint8Array.from(atob(f.base64), c => c.charCodeAt(0));
        const dt = new DataTransfer();
        dt.items.add(new File([bytes], f.name, { type: 'application/pdf' }));
        jp.filling = true;
        try {
          el.files = dt.files;
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.dispatchEvent(new Event('change', { bubbles: true }));
        } finally { jp.filling = false; }
        el.dataset.jpDone = '1';
        const box = el.closest('label, div') || el;
        box.style.outline = '2px solid #0b6e5c';
        stats.files++;
        continue;
      }

      if (!visible(el)) continue;
      const q = questionText(el);

      // ----- dropdowns: only from saved answers -----
      if (el.tagName === 'SELECT') {
        if (el.value && el.selectedIndex > 0) continue;
        const a = findSaved(q, false);
        if (!a) continue;
        const na = norm(a);
        const opt = [...el.options].find(x => norm(x.text) === na) || [...el.options].find(x => na.length > 1 && norm(x.text).includes(na));
        if (opt) { setValue(el, opt.value); mark(el, 'green'); stats.green++; }
        continue;
      }

      // ----- text boxes -----
      if (el.value && el.value.trim()) continue;
      const hay = q + ' ' + (el.name || '') + ' ' + (el.id || '') + ' ' + (el.autocomplete || '');
      if (SENSITIVE.test(hay)) continue;

      if (el.tagName === 'TEXTAREA' && COVER.test(q)) {
        const c = await getCover();
        if (c) { setValue(el, c); mark(el, 'amber'); el.dataset.jpCover = '1'; stats.amber++; continue; }
      }
      let v = findSaved(q, true);
      if (!v) { const rule = RULES.find(r => r.re.test(hay)); v = rule ? rule.get(p) : ''; }
      if (!v) v = findSaved(q, false);
      if (v) { setValue(el, v); mark(el, 'green'); stats.green++; continue; }

      if (aiOn && aiCalls < 6 && q.length >= 12 && q.length <= 300 && (el.tagName === 'TEXTAREA' || q.split(/\s+/).length >= 4)) {
        aiCalls++;
        el.dataset.jpDone = '1';
        const r = await jp.api('/ai/answer', 'POST', { question: q, jobId: opts.jobId || null });
        if (r.ok && r.data && r.data.answer) {
          setValue(el, r.data.answer);
          mark(el, 'amber');
          jp.aiFilled.add(el);
          stats.amber++;
        }
      }
    }
    return stats;
  }

  jp.fillPage = (opts = {}) => {
    if (jp._fillLock) return jp._fillLock;
    jp._fillLock = doFill(opts).catch(e => ({ green: 0, amber: 0, files: 0, error: e.message })).finally(() => { jp._fillLock = null; });
    return jp._fillLock;
  };

  // Keep filling while the page reveals more of the form (multi-step forms, modals).
  jp.autoFillLoop = getOpts => {
    if (jp._loop) return;
    jp._loop = true;
    let runs = 0, t;
    const run = async () => { if (runs++ >= 10) return; await jp.fillPage(await getOpts()); };
    setTimeout(run, 1200);
    new MutationObserver(() => { clearTimeout(t); t = setTimeout(run, 900); }).observe(document.documentElement, { childList: true, subtree: true });
  };

  chrome.runtime.onMessage.addListener(m => { if (m && m.type === 'jp.fill') jp.fillPage(m.opts || {}); });

  /* ---------- Apply button: highlight, optionally click ---------- */
  jp.highlightApply = autoClick => {
    const re = /^(easy apply|apply( now| here| for this job| for this position| on company (site|website))?|apply with .+)$/i;
    const cands = [...document.querySelectorAll('button, a, [role=button]')]
      .filter(b => visible(b) && re.test((b.innerText || '').replace(/\s+/g, ' ').trim()));
    cands.forEach(b => { b.style.outline = '3px solid #b7791f'; b.style.outlineOffset = '2px'; });
    if (cands[0]) cands[0].scrollIntoView({ block: 'center', behavior: 'smooth' });
    if (autoClick && cands[0] && !cands[0].dataset.jpClicked) { cands[0].dataset.jpClicked = '1'; cands[0].click(); }
    return cands.length;
  };

  jp.watchSubmitted = () => {
    if (jp._ws) return;
    jp._ws = setInterval(() => {
      const t = ((document.body && document.body.innerText) || '').slice(0, 8000);
      if (SUBMITTED.test(t)) { jp.submitted = true; clearInterval(jp._ws); }
    }, 1500);
  };

  /* ---------- remember what you typed (saved when you press Applied) ---------- */
  jp.collectAnswers = () => {
    const out = {};
    document.querySelectorAll('input, textarea, select').forEach(el => {
      const t = (el.type || '').toLowerCase();
      if (['hidden', 'password', 'file', 'checkbox', 'radio', 'submit', 'button', 'email', 'tel'].includes(t)) return;
      if (jp.aiFilled.has(el) || el.dataset.jpCover) return; // drafts you did not edit are not saved
      let v = el.tagName === 'SELECT' ? ((el.selectedOptions[0] || {}).text || '') : el.value;
      v = (v || '').trim();
      if (!v || v.length > 500) return;
      if (el.tagName === 'SELECT' && (el.selectedIndex < 1 || /^(select|choose|please|--)/i.test(v))) return;
      const q = questionText(el);
      if (q.length < 4 || q.length > 200) return;
      const hay = q + ' ' + (el.name || '') + ' ' + (el.id || '');
      if (SENSITIVE.test(hay) || RULES.some(r => r.re.test(hay))) return;
      out[q] = v;
    });
    return out;
  };
  const schedule = () => {
    clearTimeout(jp._pt);
    jp._pt = setTimeout(() => {
      const a = jp.collectAnswers();
      if (Object.keys(a).length) jp.bg('answers.pending', { answers: a });
    }, 700);
  };
  ['input', 'change'].forEach(ev => document.addEventListener(ev, e => {
    if (jp.filling || !e.target || !e.target.tagName) return;
    jp.aiFilled.delete(e.target);
    if (e.target.dataset) delete e.target.dataset.jpCover;
    schedule();
  }, true));

  /* ---------- floating panel (top frame only) ---------- */
  jp.initPanel = ctx => {
    if (window.top !== window || jp._panelInit) return;
    jp._panelInit = true;
    const host = document.createElement('div');
    host.style.cssText = 'all:initial;position:fixed;right:16px;bottom:16px;z-index:2147483647;';
    const root = host.attachShadow({ mode: 'open' });
    root.innerHTML = `<style>
      .box{font:13px/1.45 "Segoe UI",system-ui,sans-serif;color:#17212b;background:#fff;border:1px solid #d8dee4;border-radius:10px;box-shadow:0 6px 24px rgba(0,0,0,.18);width:310px;max-width:92vw}
      .hd{display:flex;justify-content:space-between;align-items:center;padding:8px 12px;border-bottom:1px solid #d8dee4;font-weight:700;color:#0b6e5c}
      .bd{padding:10px 12px}.row{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
      button{font:inherit;padding:6px 10px;border-radius:6px;border:1px solid #d8dee4;background:#fff;color:#17212b;cursor:pointer}
      button.pri{background:#0b6e5c;border-color:#0b6e5c;color:#fff;font-weight:600}
      button.glow{box-shadow:0 0 0 3px rgba(183,121,31,.45)}
      .muted{color:#5d6b79;font-size:12px}.sc{display:inline-block;padding:1px 8px;border-radius:6px;font-weight:700}
      .hi{background:#dff3ec;color:#0b6e5c}.mid{background:#fbefd2;color:#9a6700}.lo{background:#e9edf1;color:#5d6b79}
      a{color:#0b6e5c}
    </style><div class="box"><div class="hd"><span>JobPilot</span><button class="min" style="padding:0 8px">–</button></div><div class="bd"></div></div>`;
    document.documentElement.append(host);
    const bd = root.querySelector('.bd');
    let hidden = false, sig = '', state = null;
    root.querySelector('.min').onclick = () => { hidden = !hidden; bd.style.display = hidden ? 'none' : 'block'; };

    const jobId = () => (state && state.active && state.job ? state.job.id : (ctx.getJob() || {}).id);
    const flushTyped = async () => {
      const a = jp.collectAnswers();
      if (Object.keys(a).length) await jp.bg('answers.pending', { answers: a });
    };
    const actions = {
      fill: async () => { jp.msg = 'Filling what I can…'; refresh(); await jp.bg('fill.all', { jobId: jobId() }); jp.msg = 'Done. Check the coloured fields.'; refresh(); },
      tailor: async () => {
        const j = ctx.getJob(); if (!j) return;
        jp.msg = 'Building your resume…'; refresh();
        const r = await jp.api(`/jobs/${j.id}/tailor`, 'POST');
        if (r.ok) { jp.tailored = j.id; jp.msg = 'Queued. Resume and cover letter are ready.'; } else jp.msg = r.error;
        refresh();
      },
      applied: async () => {
        await flushTyped();
        const j = ctx.getJob();
        const r = await jp.bg('applied', { jobId: j ? j.id : null });
        if (r.ok) { if (j) jp.appliedId = j.id; jp.msg = 'Marked as applied. Your answers are remembered.'; } else jp.msg = r.error;
        refresh();
      },
      remember: async () => {
        await flushTyped();
        const r = await jp.bg('applied', { jobId: null });
        jp.msg = r.ok ? 'Your answers are remembered for next time.' : r.error;
        refresh();
      },
      next: async () => {
        await flushTyped();
        const r = await jp.bg('queue.next');
        jp.submitted = false;
        jp.msg = r.ok ? (r.data.finished ? 'Queue finished.' : 'Opening the next job…') : r.error;
        refresh();
      },
      skip: async () => { const r = await jp.bg('queue.skip'); jp.submitted = false; jp.msg = r.ok ? (r.data.finished ? 'Queue finished.' : 'Skipped for now.') : r.error; refresh(); },
      stop: async () => { await jp.bg('queue.stop'); jp.msg = 'Queue stopped.'; refresh(); }
    };
    bd.addEventListener('click', e => { const b = e.target.closest('[data-act]'); if (b && actions[b.dataset.act]) actions[b.dataset.act](); });

    function render(job) {
      let h = '';
      if (state && state.active && state.job) {
        h += `<div><b>Queue ${state.index + 1} of ${state.total}</b></div><div>${esc(state.job.title)}<br><span class="muted">${esc(state.job.company)}</span></div>`;
        if (jp.submitted) h += `<div class="muted" style="margin-top:6px;color:#0b6e5c">Looks like the application was submitted. Press Applied ✓ Next.</div>`;
        h += `<div class="row"><button class="pri" data-act="fill">Fill form</button><button data-act="next" class="${jp.submitted ? 'pri glow' : ''}">Applied ✓ Next</button><button data-act="skip">Skip for now</button><button data-act="stop">Stop queue</button></div>`;
      } else if (job) {
        const cls = job.score >= 70 ? 'hi' : job.score >= 40 ? 'mid' : 'lo';
        const queued = jp.tailored === job.id || job.status === 'QUEUED';
        const applied = jp.appliedId === job.id || job.status === 'APPLIED';
        h += `<div><span class="sc ${cls}">${job.score}</span> match${applied ? ' · applied' : ''}</div><div class="muted">${esc(job.title)}</div>`;
        if (job.matched) h += `<div class="muted" style="margin-top:4px"><b>You have:</b> ${esc(job.matched)}</div>`;
        if (job.missing) h += `<div class="muted"><b>Not on your profile:</b> ${esc(job.missing)}</div>`;
        h += `<div class="row"><button class="pri" data-act="tailor">${queued ? 'Tailor again' : 'Tailor resume &amp; queue'}</button><button data-act="applied">I applied</button><button data-act="fill">Fill form</button></div>`;
        if (queued) h += `<div class="muted" style="margin-top:6px"><a href="${esc(jp.server)}/api/jobs/${job.id}/resume.pdf" target="_blank" rel="noopener">Download tailored PDF</a></div>`;
      } else {
        h += `<div class="muted">${esc(ctx.idleText || 'Application form helper')}</div><div class="row"><button class="pri" data-act="fill">Fill form</button><button data-act="remember">Remember my answers</button></div>`;
      }
      const err = ctx.getError ? ctx.getError() : '';
      if (err) h += `<div style="margin-top:8px;color:#b42318;font-size:12px">${esc(err)}</div>`;
      if (jp.msg) h += `<div class="muted" style="margin-top:8px">${esc(jp.msg)}</div>`;
      h += `<div class="muted" style="margin-top:8px">Green = from your profile or saved answers. Amber = a draft, check it. You press Submit yourself.</div>`;
      bd.innerHTML = h;
    }

    async function refresh() {
      const r = await jp.bg('queue.state');
      state = r.ok ? r.data : null;
      const job = ctx.getJob();
      const s = JSON.stringify([state, job && [job.id, job.score, job.status], jp.msg, jp.submitted, jp.tailored, jp.appliedId, ctx.getError ? ctx.getError() : '', ctx.idleText]);
      if (s === sig) return;
      sig = s;
      render(job);
    }
    setInterval(refresh, 1500);
    refresh();
  };
})();
