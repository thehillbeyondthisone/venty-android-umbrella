const directions = [
  { id: 'quiet', name: 'Quiet Instrument', tag: 'Recommended', caption: '<strong>Clarity first.</strong> Porcelain, pine, a familiar dial, and generous space. A calm Material 3 direction with the lowest migration effort.', colors: ['#f7f9f6', '#205e50', '#d9e3dc'], palette: 'Porcelain / Pine' },
  { id: 'field', name: 'Fieldnotes', caption: '<strong>A personal object.</strong> Oat paper, warm ink, editorial type, and a linear control. Feels like a device journal, with room for observations.', colors: ['#f3eddf', '#6a4730', '#d9d1bb'], palette: 'Oat / Walnut' },
  { id: 'signal', name: 'Signal', caption: '<strong>Precision, after dark.</strong> Graphite, restrained lime, tabular figures, and crisp rules. A technical console without the current fire effects.', colors: ['#181e24', '#d4e7a0', '#37424d'], palette: 'Graphite / Lichen' }
];
// These entries are synthetic. Only the external preview button adds an entry;
// changing a target or toggling the simulated heater never awards anything.
const examples = [
  { id: 'one', target: 160, note: 'Citrus first, a soft finish.', time: 'Evening', changes: 0 },
  { id: 'two', target: 180, note: '', time: 'Afternoon', changes: 0 },
  { id: 'three', target: 185, note: 'Warm and rounded.', time: 'Evening', changes: 1 },
  { id: 'four', target: 175, note: '', time: 'Evening', changes: 0 },
  { id: 'five', target: 185, note: '', time: 'Afternoon', changes: 0 },
  { id: 'six', target: 180, note: '', time: 'Evening', changes: 0 }
];
function initialState() {
  return { target: 185, heater: 'idle', tab: 'control', collection: 'atlas', paused: false,
    sessions: examples.map(s => ({ ...s })), sampleSeen: false, postcardDismissed: false,
    compare: [160, 185], ratings: {}, comparisonNote: '' };
}
const state = Object.fromEntries(directions.map(d => [d.id, initialState()]));
const icon = id => `<svg aria-hidden="true"><use href="#${id}"/></svg>`;
const escapeText = text => text.replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const dial = `<div class="dial"><svg viewBox="0 0 272 253" aria-hidden="true"><path class="track" d="M 61 207 A 104 104 0 1 1 211 207" fill="none" stroke-width="2" stroke-linecap="round"/><path d="M 61 207 A 104 104 0 1 1 211 207" fill="none" stroke="currentColor" stroke-width="6" stroke-linecap="round" stroke-dasharray="374 490" opacity=".25"/><circle class="target-marker" cx="239" cy="125" r="6" fill="currentColor"/></svg><div class="dial-content"><div class="micro">Set temperature</div><div class="temperature"><span class="target">185</span><sup>°C</sup></div><span class="state-pill">Heater off</span></div></div>`;
const board = document.querySelector('.board');
for (const [i, d] of directions.entries()) {
  const article = document.createElement('article');
  article.className = 'direction'; article.dataset.direction = d.id;
  article.innerHTML = `<div class="option-heading"><span class="index">0${i + 1}</span><h2>${d.name}</h2>${d.tag ? `<span class="recommend">${d.tag}</span>` : ''}</div>
    <section class="phone ${d.id}" aria-label="${d.name} interactive concept">
      <div class="statusbar"><span>9:41</span><div class="statusicons">${icon('signal')}${icon('battery')}</div></div>
      <div class="device-head"><div><h3>${d.id === 'signal' ? 'VENTY / 01' : 'My Venty'}</h3><div class="connection"><i class="dot"></i><span>Connected · demo</span></div></div><div class="battery">${icon('battery')}85%</div></div>
      <div class="screen control-screen" id="${d.id}-control" role="tabpanel" aria-labelledby="${d.id}-control-tab">
        ${d.id === 'field' ? '<div class="editorial-title">Your device,<br>your way.</div>' : ''}
        ${d.id === 'quiet' ? dial : `<div class="target-block"><div class="micro">${d.id === 'signal' ? 'Target / Celsius' : 'Set temperature'}</div><div class="temperature"><span class="target">185</span><sup>°C</sup></div><span class="state-pill">Heater off</span></div>`}
        <div class="adjust">${d.id !== 'quiet' ? '<span class="adjust-label">Adjust target</span>' : ''}<button data-action="minus" aria-label="${d.name}: decrease target temperature">−</button>${d.id === 'quiet' ? '<span>Adjust target</span>' : ''}<button data-action="plus" aria-label="${d.name}: increase target temperature">+</button></div>
        <div class="range-wrap"><input aria-label="${d.name}: target temperature in Celsius" type="range" min="40" max="210" value="185"><div class="range-labels"><span>40°C</span><span>210°C</span></div></div>
        <button class="primary heater" data-action="heater">${icon('power')}<span>Start heating</span></button>
        <div class="quiet-card"><span class="small-art" aria-hidden="true">✧</span><div><strong>Your own little atlas.</strong><p>Notes, places, a trail that stays.</p></div><button data-action="open-library" aria-label="${d.name}: open collections">${icon('arrow')}</button></div>
      </div>
      <div class="screen library-screen" hidden id="${d.id}-library" role="tabpanel" aria-labelledby="${d.id}-library-tab">
        <h4 class="library-title">A collection of your own.</h4><p class="library-sub">A map, a journal, a trail.<br>Yours to keep. No finish line.</p>
        <div class="collection-picker" role="group" aria-label="${d.name} collection"><button data-collection="atlas" aria-pressed="true">Atlas</button><button data-collection="notes" aria-pressed="false">Field Notes</button><button data-collection="ember" aria-pressed="false">Ember Trail</button></div>
        <div class="collection-content"></div>
        <div class="collection-settings"><label class="pause-label"><input type="checkbox" class="pause-collecting">Do not track · pause collecting</label><p class="pause-help">Everything you have stays.<br>New sessions will be left unrecorded.</p></div>
        <details class="discoveries"><summary>Quiet discoveries</summary><ul></ul><p class="discovery-copy">Noticed after the fact. No unfinished checklist.</p></details>
      </div>
      <div class="screen rituals-screen" hidden id="${d.id}-rituals" role="tabpanel" aria-labelledby="${d.id}-rituals-tab"></div>
      <div class="tabs" role="tablist" aria-label="${d.name} screens">${[['control', 'controls', 'Control'], ['library', 'care', 'Collections'], ['rituals', 'controls', 'Rituals']].map(([id, glyph, name]) => `<button role="tab" aria-selected="${id === 'control'}" aria-controls="${d.id}-${id}" id="${d.id}-${id}-tab" data-tab="${id}" tabindex="${id === 'control' ? 0 : -1}">${icon(glyph)}${name}</button>`).join('')}</div><div class="home-indicator"></div>
    </section><p class="caption">${d.caption}</p><div class="swatches">${d.colors.map(c => `<i style="background:${c}"></i>`).join('')}<span>${d.palette}</span></div>`;
  board.append(article);
}
function rootFor(id) { return document.querySelector(`[data-direction="${id}"]`); }
function renderCollection(id) {
  const s = state[id], root = rootFor(id), content = root.querySelector('.collection-content');
  root.querySelectorAll('[data-collection]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.collection === s.collection)));
  const bands = new Set(s.sessions.map(x => Math.floor(x.target / 5) * 5));
  if (s.collection === 'atlas') {
    content.innerHTML = `<div class="poster"><div class="poster-head"><strong>Temperature Atlas</strong><span>°C · your places</span></div><div class="atlas-grid" aria-label="Temperature bands from 40 to 210 degrees Celsius">${Array.from({ length: 35 }, (_, i) => 40 + i * 5).map(t => `<span class="band ${bands.has(t) ? 'stamped' : ''}" title="${t}–${Math.min(210, t + 4)}°C${bands.has(t) ? ' · recorded' : ''}" aria-label="${t}–${Math.min(210, t + 4)} degrees${bands.has(t) ? ', recorded' : ', unrecorded'}">${bands.has(t) ? t : '·'}</span>`).join('')}</div><p>Where you have been, in 5°C bands.<br>Nothing to fill. Nothing expires.</p></div>`;
  } else if (s.collection === 'notes') {
    const latest = s.sessions.at(-1);
    content.innerHTML = `<div class="poster"><div class="poster-head"><strong>Field Notes</strong><span>A personal journal</span></div><div class="journal">${s.sessions.filter(x => x.note).map(x => `<blockquote>${escapeText(x.note)}<cite>${x.target}°C · ${x.time}</cite></blockquote>`).join('')}</div><form class="note-editor"><label for="${id}-note">Latest recorded session · ${latest.target}°C · optional</label><input id="${id}-note" maxlength="120" value="${escapeText(latest.note)}" placeholder="One line, if you feel like it" autocomplete="off"><button class="primary" type="submit">Save note</button></form><p>Manually saving a note is always your choice.</p></div>`;
  } else {
    const points = s.sessions.map((x, i) => ({ x: 20 + (i * 73) % 240, y: 22 + (i * 41) % 120 }));
    content.innerHTML = `<div class="poster"><div class="poster-head"><strong>Ember Trail</strong><span>A growing object</span></div><svg class="trail" viewBox="0 0 280 168" role="img" aria-label="A constellation with ${s.sessions.length} beads, one per recorded session">${points.slice(1).map((p, i) => `<line x1="${points[i].x}" y1="${points[i].y}" x2="${p.x}" y2="${p.y}"/>`).join('')}${points.map(p => `<circle cx="${p.x}" cy="${p.y}" r="7"/>`).join('')}</svg><p>One bead per recorded session.<br>Your Accent, no score, no destination.</p></div>`;
  }
  const discoveries = [];
  if (s.sessions.some(x => x.target <= 160)) discoveries.push('First low and slow');
  if (bands.size >= 5) discoveries.push('Explorer');
  if (s.sessions.some(x => x.changes === 0)) discoveries.push('Steady hand');
  if (s.sessions.filter(x => x.time === 'Evening').length >= 3) discoveries.push('Evening ritual');
  root.querySelector('.discoveries ul').innerHTML = discoveries.map(name => `<li>${name}</li>`).join('');
  root.querySelector('.pause-collecting').checked = s.paused;
  root.querySelector('.pause-help').innerHTML = s.paused ? 'Collecting paused. Your collection stays.<br>No backfill when you resume.' : 'Everything you have stays.<br>New sessions will be left unrecorded when paused.';
}
function renderRituals(id) {
  const s = state[id], root = rootFor(id), counts = new Map();
  s.sessions.forEach(x => { const band = Math.floor(x.target / 5) * 5; counts.set(band, (counts.get(band) || 0) + 1); });
  const favorite = [...counts].sort((a, b) => b[1] - a[1])[0][0];
  root.querySelector('.rituals-screen').innerHTML = `<h4 class="library-title">At your own pace.</h4><p class="library-sub">Small observations, whenever you like.<br>No schedule, no reminders.</p>
    <section class="ritual-card"><div class="micro">Dial-in mode</div><h5>Which felt more like you?</h5><p>Compare entries already in your journal.<br>Choose two or three temperatures.</p><input class="comparison-note" maxlength="80" aria-label="${id}: mental note for comparison" placeholder="One thing to remember…" value="${escapeText(s.comparisonNote)}">${s.compare.map((temp, i) => { const exists = s.sessions.some(x => x.target === temp); return `<div class="comparison-row"><label><input type="number" class="comparison-temp" min="40" max="210" value="${temp}" data-index="${i}" aria-label="${id}: comparison temperature ${i + 1}">°C</label>${['enjoyed', 'neutral'].map((rating, j) => `<button class="rating" data-rating="${rating}" data-index="${i}" aria-label="${id}: ${rating} ${temp} degrees" aria-pressed="${s.ratings[temp] === rating}" ${exists ? '' : 'disabled'}>${j ? '😐' : '🙂'}</button>`).join('')}</div>`; }).join('')}${s.compare.length < 3 ? '<button class="third-temp" data-action="third">Add a third temperature</button>' : ''}<p>Ratings apply to existing entries only.<br>Nothing here changes your device.</p></section>
    ${s.postcardDismissed ? '<div class="postcard-empty">Postcard put away. Nothing lost.<br><button class="reset-postcard" data-action="postcard-return">View this week’s postcard</button></div>' : `<article class="ritual-card postcard"><button class="dismiss-postcard" data-action="dismiss-postcard" aria-label="${id}: dismiss weekly postcard">×</button><div class="micro">Weekly postcard · example</div><h5>A small picture of your week.</h5><div class="postcard-field" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div><p>Favourite band ${favorite}–${favorite + 4}°C · ${s.sessions.length} recorded sessions.<br>Swipe away, or keep it. No ranking.</p></article>`}
    <div class="palette-note"><i></i><span>Seasonal colours arrive with time.<br>Session counts never speed them up.</span></div>`;
}
function update(id) {
  const s = state[id], root = rootFor(id), offline = s.heater === 'disconnected';
  root.querySelector('.target').textContent = s.target;
  root.querySelector('input[type=range]').value = s.target;
  root.querySelector('.state-pill').textContent = { idle: 'Heater off', heating: 'Heating · demo', ready: 'Ready · demo', disconnected: 'Disconnected' }[s.heater];
  root.querySelector('.connection span').textContent = offline ? 'Disconnected · demo' : 'Connected · demo';
  root.querySelector('.dot').style.opacity = offline ? '.25' : '1';
  root.querySelector('.heater span').textContent = offline ? 'Connect to use heater' : s.heater === 'idle' ? 'Start heating' : 'Turn heater off';
  root.querySelectorAll('.heater,input[type=range],[data-action="minus"],[data-action="plus"]').forEach(e => e.disabled = offline);
  if (!offline) { root.querySelector('[data-action="minus"]').disabled = s.target <= 40; root.querySelector('[data-action="plus"]').disabled = s.target >= 210; }
  const marker = root.querySelector('.target-marker');
  if (marker) { const angle = (135 + (s.target - 40) / 170 * 270) * Math.PI / 180; marker.setAttribute('cx', 136 + 104 * Math.cos(angle)); marker.setAttribute('cy', 135 + 104 * Math.sin(angle)); }
  root.querySelectorAll('[data-tab]').forEach(t => { const active = t.dataset.tab === s.tab; t.setAttribute('aria-selected', String(active)); t.tabIndex = active ? 0 : -1; });
  for (const tab of ['control', 'library', 'rituals']) root.querySelector(`.${tab}-screen`).hidden = s.tab !== tab;
}
let toastTimer;
function toast(message) { const el = document.querySelector('.toast'); el.textContent = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => el.textContent = '', 5000); }
board.addEventListener('click', e => {
  const button = e.target.closest('button'); if (!button) return;
  const id = button.closest('.direction').dataset.direction, s = state[id], action = button.dataset.action;
  if (button.dataset.tab) s.tab = button.dataset.tab;
  if (action === 'open-library') s.tab = 'library';
  if (button.dataset.collection) { s.collection = button.dataset.collection; renderCollection(id); }
  if (action === 'plus' || action === 'minus') s.target = Math.max(40, Math.min(210, s.target + (action === 'plus' ? 1 : -1)));
  if (action === 'heater') { s.heater = s.heater === 'idle' ? 'heating' : 'idle'; toast('Simulated heater only. Collections did not change.'); }
  if (button.dataset.rating) { const temp = s.compare[Number(button.dataset.index)]; s.ratings[temp] = button.dataset.rating; renderRituals(id); }
  if (action === 'third' && s.compare.length < 3) { s.compare.push(180); renderRituals(id); }
  if (action === 'dismiss-postcard') { s.postcardDismissed = true; renderRituals(id); }
  if (action === 'postcard-return') { s.postcardDismissed = false; renderRituals(id); }
  update(id);
});
board.addEventListener('input', e => {
  const id = e.target.closest('.direction')?.dataset.direction; if (!id) return;
  if (e.target.type === 'range') { state[id].target = Number(e.target.value); update(id); }
  if (e.target.classList.contains('comparison-note')) state[id].comparisonNote = e.target.value;
});
board.addEventListener('change', e => {
  const id = e.target.closest('.direction')?.dataset.direction; if (!id) return;
  if (e.target.classList.contains('pause-collecting')) { state[id].paused = e.target.checked; renderCollection(id); }
  if (e.target.classList.contains('comparison-temp')) { const temp = Number(e.target.value); if (Number.isFinite(temp) && temp >= 40 && temp <= 210) state[id].compare[Number(e.target.dataset.index)] = temp; renderRituals(id); }
});
board.addEventListener('submit', e => {
  if (!e.target.classList.contains('note-editor')) return; e.preventDefault();
  const id = e.target.closest('.direction').dataset.direction;
  state[id].sessions.at(-1).note = e.target.querySelector('input').value.trim();
  renderCollection(id); toast('Note saved in this preview. No points, no deadline.');
});
board.addEventListener('keydown', e => {
  const tab = e.target.closest('[role="tab"]');
  if (!tab || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
  e.preventDefault(); const id = tab.closest('.direction').dataset.direction, tabs = ['control', 'library', 'rituals'];
  const index = tabs.indexOf(state[id].tab);
  state[id].tab = tabs[e.key === 'Home' ? 0 : e.key === 'End' ? 2 : (index + (e.key === 'ArrowRight' ? 1 : 2)) % 3];
  update(id); document.getElementById(`${id}-${state[id].tab}-tab`).focus();
});
let swipe;
board.addEventListener('pointerdown', e => { const card = e.target.closest('.postcard'); swipe = card && !e.target.closest('button') ? { id: card.closest('.direction').dataset.direction, x: e.clientX, y: e.clientY } : null; });
board.addEventListener('pointerup', e => { if (swipe && Math.abs(e.clientX - swipe.x) > 65 && Math.abs(e.clientY - swipe.y) < 40) { state[swipe.id].postcardDismissed = true; renderRituals(swipe.id); } swipe = null; });
board.addEventListener('pointercancel', () => swipe = null);
document.querySelector('#scenario').addEventListener('change', e => { for (const d of directions) { state[d.id].heater = e.target.value; update(d.id); } });
document.querySelector('#preview-view').addEventListener('change', e => { for (const d of directions) { state[d.id].tab = e.target.value; update(d.id); } });
document.querySelector('#sample-session').addEventListener('click', () => {
  let added = 0;
  for (const d of directions) {
    const s = state[d.id];
    // Every example has a stable id. Even a paused example is consumed, so
    // resuming cannot backfill it, and repeated clicks cannot duplicate it.
    if (!s.sampleSeen) { s.sampleSeen = true; if (!s.paused) { s.sessions.push({ id: 'preview-seven', target: 190, note: '', time: 'Evening', changes: 0 }); added++; renderCollection(d.id); renderRituals(d.id); } }
  }
  toast(added ? 'Example added to unpaused collections. Repeating this preview adds no duplicate.' : 'No new collection entries. Paused periods stay unrecorded; existing examples are kept.');
});
document.querySelector('#reset').addEventListener('click', () => {
  for (const d of directions) { state[d.id] = initialState(); update(d.id); renderCollection(d.id); renderRituals(d.id); }
  document.querySelector('#scenario').value = 'idle'; document.querySelector('#preview-view').value = 'control'; document.querySelector('.toast').textContent = '';
});
for (const d of directions) { update(d.id); renderCollection(d.id); renderRituals(d.id); }
