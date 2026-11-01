// World data helpers: film tab label and greeting by hour. Pure; tested with node.
// Call WorldData.load(languages) with the parsed languages.json first (the page fetches it).
(function (root) {
  let byCode = {};
  const load = (languages) => { byCode = {}; (languages || []).forEach(l => { byCode[l.code] = l; }); };
  // The industry name where one is well known (Nollywood, Sandalwood...), else "<Language> films".
  const filmTab = (code) => { const l = byCode[code]; return !l ? 'Films' : (l.film || `${l.name} films`); };
  // Morning from 05:00, afternoon from 12:00, evening from 17:00, or the language's own hours (afternoonFrom, eveningFrom: Spanish
  // "Buenas tardes" runs to 20:00). Null where no greeting is checked for the language.
  const greeting = (code, hour) => {
    const l = byCode[code], g = l && l.greeting;
    if (!g) return null;
    const pm = g.afternoonFrom != null ? g.afternoonFrom : 12, eve = g.eveningFrom != null ? g.eveningFrom : 17;
    return hour >= 5 && hour < pm ? g.morning : hour >= pm && hour < eve ? g.afternoon : g.evening;
  };
  const WorldData = { load, filmTab, greeting };
  if (typeof module !== 'undefined' && module.exports) module.exports = WorldData; else root.WorldData = WorldData;
})(this);
