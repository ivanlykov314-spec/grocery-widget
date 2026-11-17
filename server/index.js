// Сервер синхронизации списка покупок.
// Хранит категории и товары в Postgres, после каждого изменения
// шлёт push (FCM) на все телефоны семьи, чтобы виджеты обновились.

import express from 'express';
import pg from 'pg';
import crypto from 'node:crypto';

const PORT = process.env.PORT || 3000;
const FAMILY_KEY = process.env.FAMILY_KEY;
const DATABASE_URL = process.env.DATABASE_URL;

if (!FAMILY_KEY || FAMILY_KEY.length < 6) {
  console.error('Задайте переменную FAMILY_KEY (минимум 6 символов)');
  process.exit(1);
}
if (!DATABASE_URL) {
  console.error('Задайте переменную DATABASE_URL (подключите Postgres в Railway)');
  process.exit(1);
}

// Имя FCM-топика выводится из ключа: телефоны вычисляют его так же.
export const TOPIC =
  'list-' + crypto.createHash('sha256').update(FAMILY_KEY).digest('hex').slice(0, 16);

const pool = new pg.Pool({
  connectionString: DATABASE_URL,
  ssl: process.env.PGSSL === 'true' ? { rejectUnauthorized: false } : undefined,
});

async function migrate() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS categories (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      position INTEGER NOT NULL DEFAULT 0,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    CREATE TABLE IF NOT EXISTS items (
      id SERIAL PRIMARY KEY,
      category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
      name TEXT NOT NULL,
      in_stock BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
    -- Комментарий к товару («10 штук», «без лактозы»…)
    ALTER TABLE items ADD COLUMN IF NOT EXISTS note TEXT NOT NULL DEFAULT '';
  `);
}

// ---------- Firebase (необязательно) ----------
let messaging = null;
async function initFirebase() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    console.log('FIREBASE_SERVICE_ACCOUNT не задан — push выключены, телефоны будут обновляться раз в 15 минут и по кнопке.');
    return;
  }
  try {
    const json = raw.trim().startsWith('{') ? raw : Buffer.from(raw, 'base64').toString('utf8');
    const admin = (await import('firebase-admin')).default;
    admin.initializeApp({ credential: admin.credential.cert(JSON.parse(json)) });
    messaging = admin.messaging();
    console.log('Firebase push включены, топик', TOPIC);
  } catch (e) {
    console.error('Не удалось инициализировать Firebase:', e.message);
  }
}

let pushTimer = null;
function notifyPhones() {
  if (!messaging) return;
  // Небольшая задержка, чтобы несколько быстрых изменений дали один push.
  clearTimeout(pushTimer);
  pushTimer = setTimeout(() => {
    messaging
      .send({
        topic: TOPIC,
        data: { type: 'changed', at: String(Date.now()) },
        android: { priority: 'high', ttl: 3600 * 1000 },
      })
      .catch((e) => console.error('push error:', e.message));
  }, 300);
}

// ---------- HTTP ----------
const app = express();
app.use(express.json({ limit: '100kb' }));
// Android HttpURLConnection не умеет PATCH, поэтому телефон шлёт POST
// с заголовком X-HTTP-Method-Override.
app.use((req, _res, next) => {
  const m = (req.get('X-HTTP-Method-Override') || '').toUpperCase();
  if (req.method === 'POST' && (m === 'PATCH' || m === 'DELETE')) req.method = m;
  next();
});

app.get('/', (_req, res) => res.send('Grocery widget server OK · v2 (комментарии)'));

// Railway стоит за прокси: реальный IP клиента — в X-Forwarded-For.
app.set('trust proxy', true);

// Защита от подбора ключа: после 10 неверных попыток с одного IP — пауза 15 минут.
const MAX_FAILS = 10;
const BLOCK_MS = 15 * 60 * 1000;
const fails = new Map(); // ip -> { count, until }
setInterval(() => {
  const now = Date.now();
  for (const [ip, f] of fails) if (f.until < now) fails.delete(ip);
}, 60 * 1000).unref();

app.use('/api', (req, res, next) => {
  const ip = req.ip || 'unknown';
  const now = Date.now();
  const f = fails.get(ip);
  if (f && f.until > now && f.count >= MAX_FAILS) {
    return res.status(429).json({ error: 'Слишком много попыток. Подождите 15 минут.' });
  }
  const key = req.get('X-Family-Key') || '';
  const a = Buffer.from(key);
  const b = Buffer.from(FAMILY_KEY);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    const cur = f && f.until > now ? f : { count: 0, until: 0 };
    cur.count += 1;
    cur.until = now + BLOCK_MS;
    fails.set(ip, cur);
    return res.status(401).json({ error: 'Неверный ключ' });
  }
  next();
});

const wrap = (fn) => (req, res) =>
  fn(req, res).catch((e) => {
    console.error(e);
    res.status(500).json({ error: e.message });
  });

function cleanName(v) {
  if (typeof v !== 'string') return null;
  const s = v.trim().replace(/\s+/g, ' ');
  return s.length > 0 && s.length <= 80 ? s : null;
}

async function getState() {
  const cats = await pool.query('SELECT id, name FROM categories ORDER BY position, id');
  const items = await pool.query(
    'SELECT id, category_id, name, in_stock, note FROM items ORDER BY lower(name), id'
  );
  return {
    categories: cats.rows.map((r) => ({ id: r.id, name: r.name })),
    items: items.rows.map((r) => ({
      id: r.id,
      categoryId: r.category_id,
      name: r.name,
      inStock: r.in_stock,
      note: r.note || '',
    })),
  };
}

// Настройки для телефона: топик и публичный конфиг Firebase.
app.get('/api/config', wrap(async (_req, res) => {
  const f = {
    projectId: process.env.FIREBASE_PROJECT_ID || '',
    applicationId: process.env.FIREBASE_APP_ID || '',
    apiKey: process.env.FIREBASE_API_KEY || '',
    senderId: process.env.FIREBASE_SENDER_ID || '',
  };
  const pushReady = !!messaging && !!(f.projectId && f.applicationId && f.apiKey && f.senderId);
  res.json({ topic: TOPIC, push: pushReady, firebase: pushReady ? f : null });
}));

app.get('/api/state', wrap(async (_req, res) => res.json(await getState())));

app.post('/api/categories', wrap(async (req, res) => {
  const name = cleanName(req.body?.name);
  if (!name) return res.status(400).json({ error: 'Пустое название' });
  const existing = await pool.query('SELECT id FROM categories WHERE lower(name) = lower($1)', [name]);
  if (existing.rowCount) return res.json({ id: existing.rows[0].id, state: await getState() });
  const pos = await pool.query('SELECT COALESCE(MAX(position), 0) + 1 AS p FROM categories');
  const r = await pool.query(
    'INSERT INTO categories (name, position) VALUES ($1, $2) RETURNING id',
    [name, pos.rows[0].p]
  );
  notifyPhones();
  res.json({ id: r.rows[0].id, state: await getState() });
}));

app.patch('/api/categories/:id', wrap(async (req, res) => {
  const name = cleanName(req.body?.name);
  if (!name) return res.status(400).json({ error: 'Пустое название' });
  await pool.query('UPDATE categories SET name = $1 WHERE id = $2', [name, Number(req.params.id)]);
  notifyPhones();
  res.json({ state: await getState() });
}));

app.delete('/api/categories/:id', wrap(async (req, res) => {
  await pool.query('DELETE FROM categories WHERE id = $1', [Number(req.params.id)]);
  notifyPhones();
  res.json({ state: await getState() });
}));

app.post('/api/items', wrap(async (req, res) => {
  const name = cleanName(req.body?.name);
  if (!name) return res.status(400).json({ error: 'Пустое название' });
  const categoryId = req.body?.categoryId ?? null;
  // Новый товар по умолчанию «есть дома»; можно передать inStock:false.
  const inStock = req.body?.inStock === false ? false : true;
  const r = await pool.query(
    'INSERT INTO items (name, category_id, in_stock) VALUES ($1, $2, $3) RETURNING id',
    [name, categoryId, inStock]
  );
  notifyPhones();
  res.json({ id: r.rows[0].id, state: await getState() });
}));

app.patch('/api/items/:id', wrap(async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const sets = [];
  const vals = [];
  if (typeof b.inStock === 'boolean') { vals.push(b.inStock); sets.push(`in_stock = $${vals.length}`); }
  if (b.name !== undefined) {
    const name = cleanName(b.name);
    if (!name) return res.status(400).json({ error: 'Пустое название' });
    vals.push(name); sets.push(`name = $${vals.length}`);
  }
  if (b.categoryId !== undefined) { vals.push(b.categoryId); sets.push(`category_id = $${vals.length}`); }
  if (b.note !== undefined) {
    const note = typeof b.note === 'string' ? b.note.trim().replace(/\s+/g, ' ').slice(0, 200) : '';
    vals.push(note); sets.push(`note = $${vals.length}`);
  }
  if (sets.length) {
    vals.push(id);
    await pool.query(
      `UPDATE items SET ${sets.join(', ')}, updated_at = now() WHERE id = $${vals.length}`,
      vals
    );
    notifyPhones();
  }
  res.json({ state: await getState() });
}));

app.delete('/api/items/:id', wrap(async (req, res) => {
  await pool.query('DELETE FROM items WHERE id = $1', [Number(req.params.id)]);
  notifyPhones();
  res.json({ state: await getState() });
}));

await migrate();
await initFirebase();
app.listen(PORT, () => console.log('Сервер запущен на порту', PORT));
