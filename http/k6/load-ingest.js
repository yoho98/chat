// k6 load script — ingest burst + replay latency
// usage: BASE=http://localhost:8090 k6 run http/k6/load-ingest.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8090';

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

export const options = {
  scenarios: {
    ingest: {
      executor: 'constant-vus',
      vus: 10,
      duration: '20s',
      exec: 'ingest',
    },
    replay: {
      executor: 'constant-vus',
      vus: 2,
      duration: '20s',
      exec: 'replay',
      startTime: '0s',
    },
  },
  thresholds: {
    'http_req_duration{name:ingest}':   ['p(95)<300', 'p(99)<800'],
    'http_req_duration{name:dup}':      ['p(95)<300'],
    'http_req_duration{name:timeline}': ['p(95)<500'],
    'http_req_failed':                  ['rate<0.01'],
  },
};

export function setup() {
  const r = http.post(`${BASE}/sessions`);
  const s = r.json();
  http.post(`${BASE}/sessions/${s.id}/join`, null, { headers: { 'X-User-Id': 'alice' }});
  http.post(`${BASE}/sessions/${s.id}/join`, null, { headers: { 'X-User-Id': 'bob'   }});
  return { sessionId: s.id };
}

export function ingest(data) {
  const k = uuid();
  const body = JSON.stringify({
    clientEventId: k,
    type: 'MESSAGE',
    payload: { text: `m-${__VU}-${__ITER}` },
    clientTs: new Date().toISOString(),
  });
  const headers = { headers: { 'Content-Type': 'application/json', 'X-User-Id': __VU % 2 === 0 ? 'alice' : 'bob' }};

  const r1 = http.post(`${BASE}/sessions/${data.sessionId}/events`, body, { ...headers, tags: { name: 'ingest' }});
  check(r1, {
    'ingest 200': (r) => r.status === 200,
    'not duplicate': (r) => r.json('duplicate') === false,
  });

  // 10% retry — should collapse via UNIQUE(session_id, client_event_id)
  if (Math.random() < 0.1) {
    const r2 = http.post(`${BASE}/sessions/${data.sessionId}/events`, body, { ...headers, tags: { name: 'dup' }});
    check(r2, {
      'dup 200': (r) => r.status === 200,
      'duplicate flag': (r) => r.json('duplicate') === true,
      'same seq': (r) => r.json('serverSeq') === r1.json('serverSeq'),
    });
  }
}

export function replay(data) {
  const at = new Date().toISOString();
  const r = http.get(`${BASE}/sessions/${data.sessionId}/timeline?at=${at}`, { tags: { name: 'timeline' }});
  check(r, { 'timeline 200': (r) => r.status === 200 });
  sleep(0.5);
}
