# Panduan Kode Backend Vercel untuk Duo Galing

Repository GitHub backend Anda (`https://duogaling-backend.vercel.app/`) dapat diisi dengan file-file Next.js berikut agar endpoint API Vercel Anda aktif sempurna:

### 1. `package.json`
```json
{
  "name": "duogaling-backend",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start"
  },
  "dependencies": {
    "next": "14.2.5",
    "react": "^18",
    "react-dom": "^18",
    "pg": "^8.12.0"
  }
}
```

### 2. `app/api/packages/route.js`
```javascript
import { NextResponse } from 'next/server';
import { Pool } from 'pg';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://neondb_owner:npg_MSwvl9d3eLnY@ep-cold-voice-b3sk5v3q-pooler.c-4.ap-southeast-1.aws.neon.tech/neondb?sslmode=require'
});

export async function GET() {
  try {
    const { rows } = await pool.query('SELECT * FROM packages ORDER BY id DESC');
    return NextResponse.json(rows);
  } catch (err) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}

export async function POST(req) {
  try {
    const body = await req.json();
    const { trackingNumber, recipientName, address, courierId, courierName, status, notes } = body;
    const now = Date.now();
    const query = `
      INSERT INTO packages (tracking_number, recipient_name, address, courier_id, courier_name, status, scanned_at, created_at, updated_at, notes)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      RETURNING *
    `;
    const values = [
      trackingNumber,
      recipientName,
      address,
      courierId || 'KUR001',
      courierName || 'Kurir Duo Galing',
      status || 'DIBAWA_KURIR',
      now,
      now,
      now,
      notes || ''
    ];
    const { rows } = await pool.query(query, values);
    return NextResponse.json(rows[0]);
  } catch (err) {
    return NextResponse.json({ error: err.message }, { status: 500 });
  }
}
```

### 3. `app/api/health/route.js`
```javascript
import { NextResponse } from 'next/server';

export async function GET() {
  return NextResponse.json({ status: 'ok', service: 'Duo Galing API', time: new Date().toISOString() });
}
```
