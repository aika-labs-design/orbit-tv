// Cinema: the tabs, the hero and the shelves, per person, from their profile, their loves and what they watched.
// Pure; tested with node (app/src/test/js/cinema-rows.test.js). Labels are honest: every row says why it is there
// (a love, the home or the person's own history) and nothing claims what Orbit cannot know (no playback progress).
(function (root) {
  const Taste = root.Taste || (typeof require !== 'undefined' ? require('./taste.js') : null);
  const DAY = 86400000, MIN = 60000;
  const YT = 'com.amazon.firetv.youtube', ytUrl = (id) => 'https://www.youtube.com/watch?v=' + id;
  const idOf = (t) => `${t.kind}:${t.id}`;
  const ago = (ms, now) => { const d = Math.floor((now - ms) / DAY); return d <= 0 ? 'Opened today' : d === 1 ? 'Opened yesterday' : `Opened ${d} days ago`; };
  const agoShort = (ms, now) => { const m = Math.max(1, Math.round((now - ms) / MIN)); return m < 60 ? `${m} MIN AGO` : m < 36 * 60 ? `${Math.round(m / 60)} H AGO` : `${Math.round(m / 1440)} DAYS AGO`; };
  const lower = (s) => s ? s.charAt(0).toLowerCase() + s.slice(1) : s;
  const cap = (s) => s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
  const list = (xs) => xs.length < 2 ? (xs[0] || '') : xs.slice(0, -1).join(', ') + ' and ' + xs[xs.length - 1];
  const card = (c) => Object.assign({ meta: [], trailers: [], logo: null, localTitle: null, match: null, overview: '', why: '', sub: '' }, c);

  // ---------- loves ----------
  const interestOf = (interests, id) => (interests || []).find(i => i.id === id) || null;
  const loveName = (l, interests) => l.custom ? cap(l.custom) : (interestOf(interests, l.id) || {}).name || cap(l.id);
  // Tab labels stay short: "West African cooking" is "Cooking", "Nature and wildlife" is "Nature".
  const GENERIC = ['cooking', 'food', 'cuisine', 'music', 'news'];
  function shortName(name) {
    if (!name || name.length <= 12) return name;
    const words = name.split(' '), last = words[words.length - 1];
    if (GENERIC.includes(last.toLowerCase())) return cap(last.toLowerCase());
    const first = name.split(/ and |, /)[0];
    return first !== name && first.length <= 14 ? first : name;
  }
  // Most used first (the learned weight, else the one saved on the profile, else 1); ties keep the order they were picked in.
  function rankedLoves(profile, weights) {
    const w = (l) => weights && weights[l.id] != null ? weights[l.id] : l.weight != null ? l.weight : 1;
    return ((profile && profile.loves) || []).map((l, i) => ({ l, i, w: w(l) })).sort((a, b) => b.w - a.w || a.i - b.i).map(x => x.l);
  }
  // Which shared sport card (feeds.live, its `sport`) belongs to a love: cricket, f1, motogp, tennis, or the love id for other ESPN sports.
  const SPORT_OF = { cricinfo: 'cricket', jolpica: 'f1', motogp: 'motogp' };
  function sportOf(l, interests) {
    const i = interestOf(interests, l.id); if (!i || !i.live) return null;
    if (SPORT_OF[i.live.kind]) return SPORT_OF[i.live.kind];
    return i.live.kind === 'espn' ? ((i.live.league || '').startsWith('tennis/') ? 'tennis' : l.id) : null;
  }

  // The shared sport cards (Shell.items().sport) of a love: live now or next.
  const sportCards = (l, interests, sport) => { const sp = sportOf(l, interests); return sp ? (sport || []).filter(s => (s.data || {}).sport === sp && (s.live || s.state === 'NEXT')) : []; };
  // Whether a love brings any card today (the same items loveCards turns into cards).
  function loveHas(l, f, interests, sport) {
    f = f || {};
    return sportCards(l, interests, sport).length > 0 || ['shows', 'videos', 'news'].some(k => (f[k] || []).length) ||
      (f.live || []).some(i => i.kind === 'sport' || i.kind === 'stream' || i.kind === 'story' || (i.kind === 'radio' && i.url));
  }

  // For You; the home films tab (named by the main language's film industry, only when it has titles); up to three loves with something to show; Journeys; Apps.
  // world: { filmTab(code), catalog: this person's titles, interests, loves: feeds.loves[id] (unknown: every love counts), sport: Shell.items().sport }.
  function tabs(profile, weights, world) {
    const w = world || {}, T = [{ id: 'foryou', label: 'For You' }];
    if (profile) {
      const main = (profile.languages || [])[0];
      if (main && (w.catalog || []).some(t => t.lang === main)) T.push({ id: 'films', label: w.filmTab ? w.filmTab(main) : 'Films', lang: main });
      rankedLoves(profile, weights).filter(l => !w.loves || loveHas(l, w.loves[l.id], w.interests, w.sport)).slice(0, 3)
        .forEach(l => T.push({ id: 'love:' + l.id, label: shortName(loveName(l, w.interests)), love: l.id }));
    }
    T.push({ id: 'journeys', label: 'Journeys' }, { id: 'apps', label: 'Apps' });
    return T;
  }

  // ---------- cards ----------
  // x: { history, taste, nowMs, langName, country }
  function titleCard(t, x, extra) {
    const app = t.apps && t.apps[0], h = (x.history || []).find(e => e.title && idOf(e.title) === idOf(t));
    const recent = h && x.nowMs - h.at < 7 * DAY;
    const status = recent ? (h.signal === 'watched' ? 'Watch again' : ago(h.at, x.nowMs)) : app ? 'On ' + app.name : null;
    const sub = recent ? [status, app ? app.name : null].filter(Boolean).join(' · ') : status || (t.genreNames || [])[0] || t.year || '';
    const reason = Taste.reason(x.history, t), lang = t.lang ? x.langName(t.lang) : null;
    return card(Object.assign({
      id: 't:' + idOf(t), kind: 'title', title: t.title, still: t.backdrop || t.poster || null, posterOnly: !t.backdrop && !!t.poster, poster: t.poster || null,
      logo: t.logo || null, localTitle: t.localTitle || null, trailers: t.trailers || [], appLabel: app ? app.name : null, pkg: app ? app.pkg : null,
      caption: status ? t.title + ' · ' + status : t.title, sub, overview: t.overview || '', match: Taste.match(x.taste, t), eyebrow: 'Picked for you',
      why: reason.length ? 'because you loved ' + reason.join(' and ') : lang ? 'Popular in ' + lang : '', country: x.country,
      meta: [t.year, lang, (t.genreNames || [])[0], app && x.country ? `On ${app.name} in ${x.country}` : null].filter(Boolean), data: t,
    }, extra || {}));
  }
  function liveCard(it, love, why) {
    const d = it.data || {}, on = !!it.live, next = !on && it.state === 'NEXT';
    // The competition (Premier League, IPL) and where it is on (an app here) label every live match; a match not started has no score yet.
    return card({ id: it.id, kind: 'live', title: it.name, still: d.image || it.art || null, score: next ? null : d.short || null, live: on, startMs: d.startMs || 0,
      caption: d.competition || [d.venue, d.handoffLabel].filter(Boolean).join(' · '),
      sub: [d.competition || d.venue, d.handoffLabel ? (it.pkg ? 'on ' + d.handoffLabel : d.handoffLabel) : null].filter(Boolean).join(' · '),
      pkg: it.pkg || null, appLabel: d.handoffLabel || null, eyebrow: on ? 'Live now' : next ? 'Next' : 'Result', why: why || [d.competition, d.handoffLabel].filter(Boolean).join(' · '),
      overview: it.desc || '', meta: d.format ? [d.format] : [], item: it, love: love || null });
  }
  // "Today 20:00 your time", in the city the person lives in.
  const fmt = (t, tz, opt) => new Intl.DateTimeFormat('en-GB', Object.assign({ timeZone: tz || 'UTC' }, opt)).format(new Date(t));
  const dayOf = (t, tz) => fmt(t, tz, { year: 'numeric', month: '2-digit', day: '2-digit' });
  const isToday = (ms, tz, now) => !!ms && dayOf(ms, tz) === dayOf(now, tz);
  function when(ms, tz, now) {
    const f = (t, opt) => fmt(t, tz, opt), day = (t) => dayOf(t, tz);
    const d = day(ms) === day(now) ? 'Today' : day(ms) === day(now + DAY) ? 'Tomorrow' : f(ms, { weekday: 'long' });
    return `${d} ${f(ms, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })} your time`;
  }
  // hand: the love's shared sport card (Shell.items().sport), whose hand-off app a live match opens.
  function fixtureCard(it, l, why, x, hand) {
    const on = it.state === 'in', d = (hand && hand.data) || {}, pkg = on && hand && hand.pkg ? hand.pkg : undefined;
    return card({ id: `fx:${l.id}:${it.title}:${it.startMs}`, kind: 'fixture', title: it.title, still: null, score: it.score || null, live: on,
      action: on ? (pkg ? 'Watch on ' + (d.handoffLabel || 'the app') : 'Watch') : 'When it plays', pkg, appLabel: pkg ? d.handoffLabel || null : null, handoffLabel: on ? d.handoffLabel || null : null,
      sub: on ? (it.detail || 'Live now') : it.state === 'pre' ? when(it.startMs, x.tz, x.nowMs) : ['Result', it.detail].filter(Boolean).join(' · '),
      eyebrow: on ? 'Live now' : it.state === 'pre' ? 'Next' : 'Result', why, overview: [it.detail, it.venue].filter(Boolean).join(' · '), love: l.id,
      data: { kind: 'fixture', id: it.title + ':' + it.startMs, title: it.title } });
  }
  const videoCard = (v, l, why) => card({ id: 'v:' + v.id, kind: 'video', title: v.title, still: v.thumb || null, sub: `${why} · ${v.minutes} min`,
    eyebrow: 'Video', why, overview: [v.channel, v.minutes + ' min'].filter(Boolean).join(' · '), love: l.id,
    item: { kind: 'trailer', name: v.title, url: ytUrl(v.id), pkg: YT }, data: { kind: 'video', id: v.id, title: v.title } });
  const streamCard = (v, l, why) => card({ id: 's:' + v.id, kind: 'stream', title: v.title, still: v.thumb || null, live: true, sub: [why, v.channel].filter(Boolean).join(' · '),
    eyebrow: 'Live now', why, overview: v.channel || '', love: l.id,
    item: { kind: 'trailer', name: v.title, url: ytUrl(v.id), pkg: YT }, data: { kind: 'video', id: v.id, title: v.title } });
  // A source name is isolated (as <bdi> does) so a right-to-left name cannot reorder the time beside it.
  const iso = (s) => s ? '\u2068' + s + '\u2069' : s;
  function newsCard(h, all, label, l, why, x) {
    const at = all.indexOf(h);
    return card({ id: 'n:' + (h.link || h.title), kind: 'news', title: label + ' news', headline: h.title, face: [iso(h.source), agoShort(h.publishedMs, x.nowMs)].filter(Boolean).join(' · ').toUpperCase(),
      sub: why, eyebrow: 'News', why, overview: [iso(h.source), agoShort(h.publishedMs, x.nowMs).toLowerCase()].filter(Boolean).join(' · '), love: l.id,
      reader: { label, headlines: all.slice(at).concat(all.slice(0, at)) }, data: { kind: 'news', id: h.link || h.title, title: h.title } });
  }
  // Google News ends each headline with " - <source>"; the source is shown on its own.
  const clean = (h) => h.source && h.title.endsWith(' - ' + h.source) ? Object.assign({}, h, { title: h.title.slice(0, -(h.source.length + 3)) }) : h;
  const channelCard = (c, why) => card({ id: c.id, kind: 'channel', title: c.name, still: c.art || null, caption: c.tag || '', sub: c.tag || '', eyebrow: 'Live now', why, overview: c.desc || '', item: c, live: !!c.live });
  const appCard = (a) => card({ id: a.id, kind: 'app', title: a.name, still: a.art || null, wide: true, caption: a.name, sub: a.eyebrow || '', eyebrow: 'On this TV', why: a.eyebrow || '', overview: a.desc || '', item: a, isApp: true });

  // ---------- shelves ----------
  // { profile, weights, catalog, loves: feeds.loves[id], home: feeds.homes[id], interests, world: { filmTab, langName, countryName },
  //   items: Shell.items(), taste, history, rank, nowMs, tab, homePhoto (the first home place's photograph, from the travel service) }
  function build(o) {
    const p = o.profile, W = o.world || {}, langName = W.langName || ((c) => c), countryName = W.countryName || ((c) => c);
    const x = { history: o.history || [], taste: o.taste, nowMs: o.nowMs, langName, country: p && p.living ? countryName(p.living.country) : '', tz: p && p.living && p.living.tz };
    const main = p ? p.languages[0] : null, loves = rankedLoves(p, o.weights);
    // taste order, with the main home language first (English titles are far more popular on TMDB and would bury it)
    const cat = o.catalog || [], byTaste = o.rank(cat), ranked = byTaste.filter(t => t.lang === main).concat(byTaste.filter(t => t.lang !== main));
    const it = o.items || {}, feed = o.loves || {};
    const city = p && p.home && p.home.places[0] ? p.home.places[0].name : '', homeCountry = p && p.home ? countryName(p.home.country) : '';
    const whyOf = (l) => (feed[l.id] && feed[l.id].why) || 'Because you love ' + loveName(l, o.interests);
    const appsShelf = { id: 'apps', title: 'Apps on this TV', subtitle: '', why: 'Apps on this TV',   // Android counts app use for the whole TV, not per person
      cards: (it.cont || []).map(appCard) };

    // Everything one love brings today: live first, then videos, shows and news. Each card names the love it came from.
    function loveCards(l) {
      const f = feed[l.id] || {}, why = whyOf(l);
      const sport = sportCards(l, o.interests, it.sport).map(s => liveCard(s, l.id, why));
      const sp = sportOf(l, o.interests), hand = sp ? (it.sport || []).find(s => (s.data || {}).sport === sp) : null;
      const fixtures = (f.live || []).filter(i => i.kind === 'sport' && !sport.some(s => s.title === i.title)).map(i => fixtureCard(i, l, why, x, hand));
      const streams = (f.live || []).filter(i => i.kind === 'stream').map(v => streamCard(v, l, why));
      const radio = (f.live || []).filter(i => i.kind === 'radio' && i.url).map(r => card({ id: 'r:' + r.url, kind: 'radio', title: r.title, url: r.url, still: null, live: true,
        sub: why + ' · radio', eyebrow: 'Radio', why, overview: [r.title, r.detail].filter(Boolean).join(' · '), love: l.id, data: { kind: 'radio', id: r.url, title: r.title } }));
      // live story updates (MotoGP, cricket stories) read like news, under the love's own name
      const name = loveName(l, o.interests);
      const told = (f.live || []).filter(i => i.kind === 'story').map(i => ({ title: i.title, link: i.link, source: name, publishedMs: i.startMs }));
      const stories = told.map(h => newsCard(h, told, name, l, why, x));
      const shows = (f.shows || []).map(s => {
        const t = cat.find(c => c.kind === s.kind && String(c.id) === String(s.id)) || Object.assign({ genres: [], genreNames: [], apps: [], trailers: [], logo: null, lang: null }, s, { poster: s.poster || null, backdrop: s.backdrop || null });
        return titleCard(t, x, { love: l.id, why: lower(why), id: 't:' + idOf(t) });
      });
      const heads = (f.news || []).map(clean);
      const news = heads.map(h => newsCard(h, heads, loveName(l, o.interests), l, why, x));
      return { live: sport.concat(fixtures, streams, radio, stories), videos: (f.videos || []).map(v => videoCard(v, l, why)), shows, news, why };
    }

    let shelves;
    if (o.tab === 'films' && p) {   // the main language's catalogue, by genre, then the person's other languages
      const mine = ranked.filter(t => t.lang === main), by = {};
      mine.forEach(t => { const g = (t.genreNames || [])[0]; if (g) (by[g] = by[g] || []).push(t); });
      shelves = [{ id: 'films', title: W.filmTab ? W.filmTab(main) : 'Films', subtitle: '', why: 'Films and series in ' + langName(main), cards: mine.map(t => titleCard(t, x)) }]
        .concat(Object.keys(by).filter(g => by[g].length >= 2).sort((a, b) => by[b].length - by[a].length).slice(0, 5)
          .map(g => ({ id: 'g:' + g, title: g, subtitle: '', why: g + ' in ' + langName(main), cards: by[g].map(t => titleCard(t, x)) })))
        .concat(p.languages.slice(1).map(c => ({ id: 'l:' + c, title: 'In ' + langName(c), subtitle: '', why: 'Films and series in ' + langName(c), cards: ranked.filter(t => t.lang === c).map(t => titleCard(t, x)) })));
    } else if (o.tab && o.tab.startsWith('love:') && loves.some(l => 'love:' + l.id === o.tab)) {
      const l = loves.find(v => 'love:' + v.id === o.tab), c = loveCards(l);
      shelves = [['live', 'Live and next'], ['videos', 'Videos'], ['shows', 'Films and series'], ['news', 'News']]
        .map(([k, title]) => ({ id: 'l:' + k, title, subtitle: c.why, why: c.why, cards: c[k] }));
    } else if (o.tab === 'apps') shelves = [appsShelf];
    else {
      const recent = x.history.filter(e => e.title && e.signal !== 'quick' && o.nowMs - e.at < 7 * DAY).map(e => cat.find(t => idOf(t) === idOf(e.title))).filter(Boolean);
      const fresh = ranked.filter(t => !x.history.some(e => e.title && idOf(e.title) === idOf(t) && o.nowMs - e.at < 2 * DAY));
      const pick = fresh[0] || ranked[0], used = new Set();
      const take = (ts, n) => ts.filter(t => !used.has(idOf(t))).slice(0, n).map(t => { used.add(idOf(t)); return t; });
      const per = loves.map(l => ({ l, c: loveCards(l) }));

      // Tonight: what is live (or starts today) for a love, the pick, a new video, home news, a live stream, then what was watched lately.
      const live = per.flatMap(v => v.c.live.filter(c => c.kind === 'live' && (c.live || isToday(c.startMs, x.tz, o.nowMs))));
      const soon = per.flatMap(v => v.c.live.filter(c => c.kind === 'fixture' && (c.live || /^Today /.test(c.sub))));
      const tonight = live.concat(soon).slice(0, 2);
      if (pick) tonight.push(titleCard(take([pick], 1)[0], x));
      tonight.push(...per.map(v => v.c.videos[0]).filter(Boolean).slice(0, 2));
      const homeHeads = ((o.home && o.home.news && o.home.news.headlines) || []).map(clean), hn = homeHeads[0];
      if (hn && p) {
        const sources = [...new Set(homeHeads.map(h => h.source).filter(Boolean))].slice(0, 2).map(iso);
        const langs = p.languages.filter(c => c.length === 2).slice(0, 2).map(langName);
        tonight.push(card({ id: 'homenews', kind: 'news', title: 'Home news', headline: hn.title, face: `${city} · ${agoShort(hn.publishedMs, o.nowMs)}`.toUpperCase(),
          sub: sources.concat(langs.length ? ['in ' + list(langs)] : []).join(' · '), eyebrow: 'Home news', why: 'News from ' + city, overview: [iso(hn.source), agoShort(hn.publishedMs, o.nowMs).toLowerCase()].filter(Boolean).join(' · '),
          reader: { label: 'Home news · ' + city, headlines: homeHeads }, data: { kind: 'news', id: 'home:' + hn.title, title: hn.title } }));
      }
      tonight.push(...per.map(v => v.c.live.find(c => c.kind === 'stream')).filter(Boolean).slice(0, 1));
      tonight.push(...take(recent, 2).map(t => titleCard(t, x)), ...take(fresh, Math.max(0, 12 - tonight.length - 2)).map(t => titleCard(t, x)));
      const inTonight = new Set(tonight.map(c => c.id));
      const loveRows = per.map(({ l, c }) => ({ id: 'love:' + l.id, title: c.why, subtitle: '', why: c.why,
        // two headlines here (the love's tab has them all): many serif headlines cost a long first layout on this TV
        cards: c.live.concat(c.videos, c.shows, c.news.slice(0, 2)).filter(k => !inTonight.has(k.id)).slice(0, 12) }));

      const loved = x.history.find(e => e.title && e.signal !== 'quick' && cat.some(t => idOf(t) === idOf(e.title)));
      // the main language's titles, else the first of the person's languages that has any; none: no shelf (never the whole catalogue under another name)
      const popLang = p ? p.languages.find(c => cat.some(t => t.lang === c)) : null, popular = cat.filter(t => t.lang === popLang);
      const because = loved
        ? { id: 'because', title: 'Because you loved ' + loved.title.title, subtitle: (loved.title.genreNames || [])[0] || '', why: 'Because you loved ' + loved.title.title,
          cards: ranked.filter(t => idOf(t) !== idOf(loved.title) && (t.genres || []).some(g => (loved.title.genres || []).includes(g))).slice(0, 12).map(t => titleCard(t, x)) }
        : { id: 'because', title: 'Popular in ' + langName(popLang), subtitle: 'What people are watching this week', why: 'Popular in ' + langName(popLang),
          cards: popular.slice().sort((a, b) => b.popularity - a.popularity).slice(0, 12).map(t => titleCard(t, x)) };
      const onapps = ranked.filter(t => t.apps && t.apps.length), langsOn = [...new Set(onapps.map(t => t.lang).filter(Boolean))].map(langName);
      const tag = o.home && o.home.pack && o.home.pack.channelTag;
      const chLive = tag ? (it.ch || []).filter(c => c.live && (c.tag || '').includes(tag)).map(c => channelCard(c, 'Live from ' + homeCountry)) : [];
      const homeCards = [
        card({ id: 'home:mode', kind: 'homemode', title: 'Feels like ' + homeCountry, still: o.homePhoto || null, caption: city + ' time, radio and the sounds of home', sub: city + ' time, radio and the sounds of home', eyebrow: 'Home',
          overview: `Home weather, festivals, home news, radio and the sounds of ${city}.` }),
        card({ id: 'home:classic', kind: 'classic', title: 'Classic home', caption: 'The previous home screen', sub: 'The previous home screen', eyebrow: 'Home',
          overview: `Rows of tiles, moods and the Netflix Top 10 for ${x.country || 'where you live'}.` }),
      ];
      shelves = [
        { id: 'tonight', title: 'Tonight, for you', subtitle: 'live, new and close to home', why: 'Live, new and close to ' + city, cards: tonight },
        ...loveRows.slice(0, 1), because, ...loveRows.slice(1),
        { id: 'onapps', title: 'On your apps', subtitle: [...new Set(onapps.map(t => t.apps[0].name))].join(' · '), why: `In ${list(langsOn)} on your apps in ${x.country}`, cards: onapps.slice(0, 12).map(t => titleCard(t, x)) },
        { id: 'live', title: 'Live from home', subtitle: homeCountry, why: 'Live from ' + homeCountry, cards: chLive },
        { id: 'home', title: 'Home', subtitle: city, why: `${city}, ${homeCountry}`, cards: o.home && p ? homeCards : [] },
      ];
      if (!shelves.some(s => s.cards.length)) shelves = [appsShelf];
    }
    shelves = shelves.filter(s => s.cards.length);
    // the featured title: one in the main language when the first shelf has one, else its first title, else its first card
    const first = shelves.length ? shelves[0].cards : [];
    const hero = first.find(c => c.kind === 'title' && c.data && c.data.lang === main) || first.find(c => c.kind === 'title') || first[0] || null;
    return { hero, shelves };
  }

  // A history entry for something opened, with the love it was opened from and the minutes it was watched (both kept on later updates).
  function record(history, c, signal, nowMs, minutes) {
    const title = c.data || { kind: c.kind, id: c.id, title: c.title };
    const old = (history || []).find(e => e.title && idOf(e.title) === idOf(title));
    const h = Taste.remember(history, title, signal, nowMs), e = Object.assign({}, h[0]);
    const love = c.love || (old && old.love); if (love) e.love = love;
    const m = Math.max((old && old.minutes) || 0, minutes || 0); if (m) e.minutes = Math.round(m);
    h[0] = e; return h;
  }
  // Whether selecting a card counts as opening it (history, taste): a title only when it opens on one of the person's apps.
  const counts = (c) => c.kind !== 'title' || !!c.pkg;
  // Save learned weights only when one moved by 0.05 or more from what the profile holds.
  const weightsChanged = (profile, weights) => ((profile && profile.loves) || []).some(l => weights && weights[l.id] != null && Math.abs((l.weight != null ? l.weight : 1) - weights[l.id]) >= 0.05);
  // The "Orbit noticed" sentence: how many recent things pointed at the interest, and the latest one.
  const WORDS = ['no', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten'];
  function noticedLine(history, interest, nowMs) {
    const terms = [interest.name].concat(interest.terms || []);
    const hits = (history || []).filter(e => e.title && e.signal !== 'quick' && e.at && nowMs - e.at <= 60 * DAY && (e.love === interest.id || Taste.termIn([e.title.title, e.title.overview].filter(Boolean).join(' . '), terms)))
      .sort((a, b) => b.at - a.at);
    const n = hits.length, t = n ? hits[0].title.title : '';
    return `You opened ${WORDS[n] || n} things about ${interest.name} lately${t ? `, most recently "${t}"` : ''}.`;
  }

  // The same card can change in place (a live score, a logo or trailer that arrived later): the hero must follow.
  const heroChanged = (a, b) => !a || !b || ['score', 'logo', 'still', 'caption', 'why', 'sub'].some(k => a[k] !== b[k]) || (a.trailers || []).length !== (b.trailers || []).length;
  // Where focus lands after a rebuild: a tab change or Back resets to the featured title; a data refresh keeps your place.
  function landing(r, o) {
    const heroAt = () => ({ ri: 0, ci: Math.max(0, r.shelves.length ? r.shelves[0].cards.findIndex(c => r.hero && c.id === r.hero.id) : 0) });
    if (o.reset) return heroAt();
    const k = r.shelves.findIndex(s => s.id === o.keepShelf); if (k < 0) return heroAt();
    return { ri: k, ci: Math.max(0, r.shelves[k].cards.findIndex(c => c.id === o.keepCard)) };
  }

  // What a data push may change on screen, as one string: equal keys mean a rebuild would draw the same page (fewer rebuilds keep shelf frame times down).
  // Left out on purpose: countdowns, fetch times and "ago" labels Cinema does not show, and the home weather; time counts in ten-minute steps.
  // o: { profile, tab, catalogN (changes with the catalogue), loves, home, items, history and taste (stored strings), nowMs, extra }.
  function inputsKey(o) {
    const it = o.items || {}, h = o.home || {};
    const sport = (it.sport || []).map(s => [s.id, s.name, s.live, s.state, s.pkg, Object.assign({}, s.data, { line1: 0, line2: 0, fetchedMs: 0 })]);
    const ch = (it.ch || []).map(c => [c.id, c.name, c.art, c.tag, c.desc, c.live]), cont = (it.cont || []).map(a => [a.id, a.name, a.art, a.desc]);
    return JSON.stringify([o.profile, o.tab, o.catalogN, o.loves, h.pack, h.news, sport, ch, cont, o.history, o.taste, Math.floor(o.nowMs / (10 * MIN)), o.extra]);
  }

  // What Cinema launched, read back on return. Older installs kept it under the Stage name: moved once (a newer pick wins).
  const PICK_KEY = 'orbit.cinema.lastPick', OLD_PICK_KEY = 'orbit.stage.lastPick';
  function adoptOldPick(storage) {
    const old = storage.getItem(OLD_PICK_KEY); if (old == null) return;
    if (storage.getItem(PICK_KEY) == null) storage.setItem(PICK_KEY, old);
    storage.removeItem(OLD_PICK_KEY);
  }
  const CinemaRows = { PICK_KEY, adoptOldPick, counts, build, tabs, inputsKey, titleCard, shortName, rankedLoves, record, weightsChanged, noticedLine, heroChanged, landing, loveName };
  if (typeof module !== 'undefined' && module.exports) module.exports = CinemaRows; else root.CinemaRows = CinemaRows;
})(this);
