// Naukri and Indeed: score badges on search results, a panel on job pages, and the apply queue.
// The page layouts of these sites change often. If a selector below stops matching, adjust the lists at the top.
(async () => {
  const jp = window.__jp;
  const isNaukri = /(^|\.)naukri\.com$/.test(location.hostname);
  const source = isNaukri ? 'Naukri' : 'Indeed';
  const o = (await jp.bg('opts')).data || {};
  jp.server = o.server || jp.server;

  const txt = el => (el ? el.innerText.replace(/\s+/g, ' ').trim() : '');
  const first = (root, sels) => { for (const s of sels) { const e = root.querySelector(s); if (e) return e; } return null; };

  let lastError = '';       // shown in the panel so you can see why a score is missing
  let scored = 0;           // listings scored on this page
  const friendly = e => /Not connected/i.test(e) ? 'Not connected. In JobPilot, open Settings and press Connect extension.'
    : /not running/i.test(e) ? 'JobPilot is not running. Start the app, then reload this page.' : e;
  const track = r => { lastError = r.ok ? '' : friendly(r.error || 'Something went wrong'); return r; };

  const CARDS = isNaukri
    ? ['div.srp-jobtuple-wrapper', 'div.cust-job-tuple', 'article.jobTuple', 'div[class*="jobTuple"]']
    : ['div.job_seen_beacon', 'li div.cardOutline', 'td.resultContent'];

  function keyFromUrl(u) {
    try {
      const url = new URL(u, location.href);
      if (!isNaukri) { const jk = url.searchParams.get('jk') || url.searchParams.get('vjk'); if (jk) return jk; }
      else { const m = url.pathname.match(/(\d{8,})\/?$/); if (m) return m[1]; }
      return url.origin + url.pathname;
    } catch (e) { return u; }
  }

  /* ---------- search results: capture each listing and show a badge ---------- */
  function readCard(card) {
    if (isNaukri) {
      const a = first(card, ['a.title', 'a[class*="title"]', 'h2 a']);
      if (!a || !a.href) return null;
      const url = a.href.split('?')[0];
      return { titleEl: a, item: { key: keyFromUrl(url), source, title: txt(a), url,
        company: txt(first(card, ['a.comp-name', 'a[class*="comp-name"]', '[class*="comp-name"]'])),
        location: txt(first(card, ['span.locWdth', 'span.loc', '[class*="locWdth"]', '[class*="location"]'])),
        description: txt(first(card, ['.job-desc', '[class*="job-desc"]'])) } };
    }
    const a = first(card, ['a.jcs-JobTitle', 'h2.jobTitle a', 'h2 a']);
    if (!a) return null;
    const jk = a.getAttribute('data-jk') || keyFromUrl(a.href);
    if (!jk) return null;
    return { titleEl: a, item: { key: jk, source, title: txt(first(card, ['h2.jobTitle span[title]', 'h2 span'])) || txt(a),
      url: `${location.origin}/viewjob?jk=${jk}`,
      company: txt(first(card, ['[data-testid="company-name"]', 'span.companyName'])),
      location: txt(first(card, ['[data-testid="text-location"]', 'div.companyLocation'])),
      description: txt(first(card, ['[data-testid="belowJobSnippet"]', 'div.job-snippet', '.underShelfFooter'])) } };
  }

  // Used when none of the card selectors match: find job links directly and look around them.
  function readLink(a) {
    const title = txt(a);
    if (title.length < 5 || title.length > 150) return null;
    const card = a.closest('article, li, [class*="tuple"], [class*="result"], [class*="job"]') || a.parentElement.parentElement || a.parentElement;
    if (isNaukri) {
      const url = a.href.split('?')[0];
      return { titleEl: a, item: { key: keyFromUrl(url), source, title, url,
        company: txt(first(card, ['[class*="comp-name"]', '[class*="company"]'])),
        location: txt(first(card, ['[class*="loc"]'])),
        description: txt(first(card, ['[class*="desc"]'])) } };
    }
    const jk = a.getAttribute('data-jk') || keyFromUrl(a.href);
    if (!jk || jk.startsWith('http')) return null;
    return { titleEl: a, item: { key: jk, source, title, url: `${location.origin}/viewjob?jk=${jk}`,
      company: txt(first(card, ['[data-testid="company-name"]', '[class*="company"]'])),
      location: txt(first(card, ['[data-testid="text-location"]', '[class*="ocation"]'])),
      description: txt(first(card, ['[data-testid="belowJobSnippet"]', '[class*="snippet"]'])) } };
  }

  function badge(titleEl, res) {
    if (!titleEl || !res) return;
    const hi = res.score >= 70, mid = res.score >= 40;
    // reuse the badge if this listing already has one (several selectors can match the same card)
    let b = titleEl.nextElementSibling;
    if (!(b && b.dataset && b.dataset.jpBadge)) {
      b = document.createElement('span');
      b.dataset.jpBadge = '1';
      titleEl.insertAdjacentElement('afterend', b);
    }
    b.textContent = (res.status === 'APPLIED' ? 'Applied · ' : '') + 'JobPilot ' + res.score;
    b.title = 'You have: ' + (res.matched || '-') + '\nNot on your profile: ' + (res.missing || '-');
    b.style.cssText = `display:inline-block;margin-left:8px;padding:1px 7px;border-radius:5px;font:600 12px/18px Arial,sans-serif;vertical-align:middle;` +
      `background:${hi ? '#dff3ec' : mid ? '#fbefd2' : '#e9edf1'};color:${hi ? '#0b6e5c' : mid ? '#9a6700' : '#5d6b79'}`;
  }

  let scanning = false;
  async function scan() {
    if (scanning) return;
    scanning = true;
    try {
      const allCards = [...new Set(CARDS.flatMap(s => [...document.querySelectorAll(s)]))];
      const rows = [];
      if (allCards.length) {
        for (const c of allCards.filter(c => !c.dataset.jpSeen)) {
          const r = readCard(c);
          c.dataset.jpSeen = '1';
          if (r && r.item.title) rows.push(r);
        }
      } else {
        const sel = isNaukri ? 'a[href*="/job-listings-"]' : 'a[data-jk], a[href*="jk="]';
        for (const a of document.querySelectorAll(sel)) {
          if (a.dataset.jpSeen || a.closest('#jobpilot-panel-host')) continue;
          const r = readLink(a);
          a.dataset.jpSeen = '1';
          if (r) rows.push(r);
        }
      }
      const seenLinks = new Set();
      const unique = rows.filter(r => !seenLinks.has(r.titleEl) && seenLinks.add(r.titleEl));
      for (let i = 0; i < unique.length; i += 40) {
        const batch = unique.slice(i, i + 40);
        const res = track(await jp.bg('capture', { jobs: batch.map(b => b.item) }));
        if (!res.ok) { batch.forEach(b => { if (b.titleEl) delete b.titleEl.dataset.jpSeen; }); allCards.forEach(c => delete c.dataset.jpSeen); break; }
        const byKey = new Map(batch.map(b => [b.item.key, b.titleEl]));
        res.data.forEach(r => { badge(byKey.get(r.key), r); scored++; });
      }
    } finally { scanning = false; }
  }
  let scanTimer;
  new MutationObserver(() => { clearTimeout(scanTimer); scanTimer = setTimeout(scan, 800); }).observe(document.documentElement, { childList: true, subtree: true });
  scan();
  setInterval(scan, 5000); // safety net for pages that load results without obvious DOM changes

  /* ---------- job page: capture the full description ---------- */
  function readJobPage() {
    if (isNaukri) {
      if (!/job-listings/.test(location.pathname)) return null;
      const url = location.origin + location.pathname;
      const main = document.querySelector('main') || document.body;
      return { key: keyFromUrl(url), source, url,
        title: txt(first(document, ['h1', '[class*="jd-header-title"]'])),
        company: txt(first(document, ['[class*="jd-header-comp-name"] a', '[class*="comp-name"]'])),
        location: txt(first(document, ['[class*="location"] a', '[class*="location"]'])),
        description: txt(first(document, ['[class*="job-desc-container"]', 'section[class*="job-desc"]', '[class*="dang-inner-html"]', '[class*="JDC"]'])) || txt(main).slice(0, 15000) };
    }
    const descEl = document.querySelector('#jobDescriptionText');
    if (!descEl) return null;
    const sp = new URL(location.href).searchParams;
    const jk = sp.get('jk') || sp.get('vjk');
    return { key: jk || keyFromUrl(location.href), source, url: jk ? `${location.origin}/viewjob?jk=${jk}` : location.href,
      title: txt(first(document, ['h1[class*="JobInfoHeader-title"]', '[data-testid="jobsearch-JobInfoHeader-title"]', 'h1'])).replace(/\s*-\s*job post$/i, ''),
      company: txt(first(document, ['[data-testid="inlineHeader-companyName"]', '[data-testid="jobsearch-JobInfoHeader-companyName"]', 'div[data-company-name="true"]'])),
      location: txt(first(document, ['[data-testid="inlineHeader-companyLocation"]', '[data-testid="job-location"]'])),
      description: txt(descEl) };
  }

  let cur = null, lastSig = '', started = false;
  async function tick() {
    const page = readJobPage();
    if (page && page.title && page.description.length > 80) {
      const sig = page.key + '|' + page.description.length;
      if (sig !== lastSig) {
        lastSig = sig;
        const r = track(await jp.bg('capture', { jobs: [page] }));
        if (r.ok && r.data[0]) cur = { ...r.data[0], title: page.title };
        else lastSig = ''; // try again on the next tick
      }
    } else if (!page) {
      cur = null;
    }
    const qr = await jp.bg('queue.state');
    const st = qr.ok ? qr.data : null;
    if (st && st.active && !started) {
      started = true;
      jp.watchSubmitted();
      setTimeout(() => jp.highlightApply(!!o.autoClick), 1500);
      jp.autoFillLoop(() => ({ jobId: st.job.id }));
    }
  }

  jp.initPanel({
    getJob: () => cur,
    getError: () => lastError,
    get idleText() { return `Open a job to see its match score here. Listings scored on this page: ${scored}.`; }
  });
  tick();
  setInterval(tick, 1500);
})();
