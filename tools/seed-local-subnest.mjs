// Only targets a fresh, isolated loopback server. Never point this fixture at production.
const origin = 'http://localhost:43210';
const credentials = { email: 'android-local@example.com', password: 'Local-test-only-2749' };
let cookies = [];
async function api(path, body) {
    const response = await fetch(origin + '/api/app/' + path, {
        method: body === undefined ? 'GET' : 'POST',
        headers: {
            'Content-Type': 'application/json', Origin: origin,
            Cookie: cookies.join('; '),
            'X-Renewlet-CSRF': cookies.find(c => c.startsWith('renewlet_csrf='))?.split('=')[1] ?? '',
        },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const next = response.headers.getSetCookie().map(c => c.split(';')[0]);
    cookies = [...cookies.filter(c => !next.some(n => n.split('=')[0] === c.split('=')[0])), ...next];
    const text = await response.text();
    if (!response.ok) throw new Error(`${path}: ${response.status} ${text}`);
    return text ? JSON.parse(text).data : null;
}
const status = await api('setup');
if (status.setupRequired) await api('setup', { ...credentials, name: 'Android local test' });
await api('auth/login', credentials);
const existing = (await api('subscriptions/index')).subscriptions;
if (existing.some(row => !['Netflix', 'YouTube', 'Spotify'].includes(row.platformName))) {
    throw new Error('Refusing to seed an unrelated database');
}
const now = new Date();
const date = offset => {
    const d = new Date(now);
    d.setDate(d.getDate() + offset);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
for (const [name, accountNumber, offset, sharing] of [['Netflix', 1, 0, true], ['Netflix', 2, -2, false], ['YouTube', 1, 3, true], ['Spotify', 1, 12, false]]) {
    if (existing.some(row => row.platformName === name && row.accountNumber === accountNumber)) continue;
    await api('subscriptions', {
        name, platformName: name, accountNumber, logo: null, price: '45', currency: 'CNY', billingCycle: 'monthly',
        customDays: null, customCycleUnit: null, oneTimeTermCount: null, oneTimeTermUnit: null,
        category: 'entertainment', status: 'active', paymentMethod: null, startDate: date(-30), nextBillingDate: date(offset),
        autoRenew: false, autoCalculateNextBillingDate: false, pinned: false, publicHidden: false,
        trialEndDate: null, website: null, notes: null, tags: [], reminderDays: 3,
        repeatReminderEnabled: false, repeatReminderInterval: '1h', repeatReminderWindow: '72h', costSharing: null,
        familySharing: sharing ? { enabled: true, loginAccount: `${name.toLowerCase()}@example.com`, capacity: 5 } : null,
        extra: {},
    });
}
console.log('Seeded isolated local server with 4 subscriptions and 2 shared accounts.');
