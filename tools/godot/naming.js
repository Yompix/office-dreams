// Одинаковые имена объектов мест для выгрузки моделей (export_world.js) и записи кадров (bake_shots.js):
// подвижные части — по ключам места (door7, leaves_0…), лампы — по ключам или light_<n> по порядку обхода сцены.
module.exports = `(() => { for (const L of Object.values(window.__dbgL)) {
  for (const [k, v] of Object.entries(L)) { const list = Array.isArray(v) ? v : [v];
    list.forEach((o, i) => { if (o && o.isObject3D && !o.isScene && !o.name) o.name = list.length > 1 ? k + '_' + i : k; }); }
  let n = 0; L.S.traverse(o => { if (o.isLight && !o.name) o.name = 'light_' + (n++); }); } })();`;
