import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

import {
	PredictionClient,
	PredictionRequestError,
	loadNextPredictionHistoryState,
	loadNextPredictionState,
	loadPredictionHistoryState,
	loadPredictionState,
} from './risk/prediction-client.ts';

const appDir = dirname(fileURLToPath(import.meta.url));

function read(relativePath: string): string {
	return readFileSync(join(appDir, relativePath), 'utf8');
}

function walkTsAndHtml(dir: string): string[] {
	const found: string[] = [];
	for (const entry of readdirSync(dir, { withFileTypes: true })) {
		const path = join(dir, entry.name);
		if (entry.isDirectory()) {
			found.push(...walkTsAndHtml(path));
			continue;
		}
		if (entry.name.endsWith('.spec.ts')) {
			continue;
		}
		if (entry.name.endsWith('.ts') || entry.name.endsWith('.html')) {
			found.push(path);
		}
	}
	return found;
}

test('routes expose onboarding and risk shells', () => {
	const routes = read('app.routes.ts');
	assert.match(routes, /path:\s*'onboarding'/);
	assert.match(routes, /path:\s*'risk'/);
	assert.match(routes, /redirectTo:\s*'onboarding'/);
});

test('risk workspace links each account to its score-history route', () => {
	const routes = read('app.routes.ts');
	const riskPage = read('risk/risk.html');
	assert.match(routes, /path:\s*'risk\/:accountExternalId\/history'/);
	assert.match(routes, /RiskHistoryPage/);
	assert.match(riskPage, /routerLink/);
	assert.match(riskPage, /prediction\.account_external_id/);
});

test('shell has no client-side tenant switch', () => {
	for (const file of walkTsAndHtml(appDir)) {
		const text = readFileSync(file, 'utf8');
		assert.doesNotMatch(text, /X-Tenant-ID/);
		assert.doesNotMatch(text, /tenant-id/i);
		assert.doesNotMatch(text, /tenant switch/i);
		assert.doesNotMatch(text, /X-Api-Key/);
		assert.doesNotMatch(text, /localStorage/);
		assert.doesNotMatch(text, /sessionStorage/);
		assert.doesNotMatch(text, /document\.cookie/);
		assert.doesNotMatch(text, /access[_-]?token/i);
		assert.doesNotMatch(text, /refresh[_-]?token/i);
		assert.doesNotMatch(text, /id[_-]?token/i);
	}
});

test('package manifest stays free of MongoDB and Redis', () => {
	const manifest = JSON.parse(readFileSync(join(appDir, '../../package.json'), 'utf8')) as {
		dependencies?: Record<string, string>;
		devDependencies?: Record<string, string>;
	};
	const names = [
		...Object.keys(manifest.dependencies ?? {}),
		...Object.keys(manifest.devDependencies ?? {}),
	];
	for (const name of names) {
		assert.doesNotMatch(name, /mongo/i);
		assert.doesNotMatch(name, /redis/i);
	}
});

test('local development proxies only API and authentication paths to platform-service', () => {
	const workspace = JSON.parse(readFileSync(join(appDir, '../../angular.json'), 'utf8')) as {
		projects: { console: { architect: { serve: { options: { proxyConfig?: string } } } } };
	};
	assert.equal(
		workspace.projects.console.architect.serve.options.proxyConfig,
		'proxy.conf.json',
	);

	const proxy = JSON.parse(readFileSync(join(appDir, '../../proxy.conf.json'), 'utf8')) as
		Record<string, unknown>;
	assert.deepEqual(Object.keys(proxy).sort(), ['/login', '/oauth2', '/v1']);
	for (const path of Object.keys(proxy)) {
		assert.deepEqual(proxy[path], {
			target: 'http://localhost:8080',
			changeOrigin: false,
		});
	}
});

test('prediction client uses only the same-origin browser session', async () => {
	let requestedUrl = '';
	let requestedInit: RequestInit | undefined;
	const client = new PredictionClient(async (url, init) => {
		requestedUrl = url;
		requestedInit = init;
		return jsonResponse({
			items: [prediction('acct-101')],
			next_cursor: 'opaque-next-page',
		});
	});

	const response = await client.listCurrent();

	assert.equal(requestedUrl, '/v1/predictions?limit=50');
	assert.deepEqual(requestedInit, {
		method: 'GET',
		credentials: 'same-origin',
		cache: 'no-store',
		headers: { Accept: 'application/json' },
	});
	assert.doesNotMatch(JSON.stringify(requestedInit), /X-Api-Key|Authorization|Bearer/i);
	assert.equal(response.items[0]?.account_external_id, 'acct-101');
	assert.equal(response.next_cursor, 'opaque-next-page');
});

test('prediction client encodes the opaque cursor for later current-risk pages', async () => {
	let requestedUrl = '';
	let requestedInit: RequestInit | undefined;
	const client = new PredictionClient(async (url, init) => {
		requestedUrl = url;
		requestedInit = init;
		return jsonResponse({ items: [prediction('acct-next')] });
	});

	await client.listCurrent('opaque+/=cursor');

	assert.equal(requestedUrl, '/v1/predictions?limit=50&cursor=opaque%2B%2F%3Dcursor');
	assert.deepEqual(requestedInit, {
		method: 'GET',
		credentials: 'same-origin',
		cache: 'no-store',
		headers: { Accept: 'application/json' },
	});
	assert.doesNotMatch(JSON.stringify(requestedInit), /X-Api-Key|Authorization|Bearer/i);
});

test('prediction-history client encodes the account path and uses only the same-origin session', async () => {
	let requestedUrl = '';
	let requestedInit: RequestInit | undefined;
	const client = new PredictionClient(async (url, init) => {
		requestedUrl = url;
		requestedInit = init;
		return jsonResponse({ items: [prediction('account/with spaces')] });
	});

	const response = await client.listHistory('account/with spaces');

	assert.equal(requestedUrl, '/v1/accounts/account%2Fwith%20spaces/prediction-history?limit=50');
	assert.deepEqual(requestedInit, {
		method: 'GET',
		credentials: 'same-origin',
		cache: 'no-store',
		headers: { Accept: 'application/json' },
	});
	assert.doesNotMatch(JSON.stringify(requestedInit), /X-Api-Key|Authorization|Bearer/i);
	assert.equal(response.items[0]?.account_external_id, 'account/with spaces');
});

test('prediction-history client encodes the opaque cursor for later account pages', async () => {
	let requestedUrl = '';
	let requestedInit: RequestInit | undefined;
	const client = new PredictionClient(async (url, init) => {
		requestedUrl = url;
		requestedInit = init;
		return jsonResponse({ items: [prediction('acct-history')] });
	});

	await client.listHistory('account/with spaces', 'history+/=cursor');

	assert.equal(
		requestedUrl,
		'/v1/accounts/account%2Fwith%20spaces/prediction-history?limit=50&cursor=history%2B%2F%3Dcursor',
	);
	assert.deepEqual(requestedInit, {
		method: 'GET',
		credentials: 'same-origin',
		cache: 'no-store',
		headers: { Accept: 'application/json' },
	});
	assert.doesNotMatch(JSON.stringify(requestedInit), /X-Api-Key|Authorization|Bearer/i);
});

test('prediction-history state exposes ready, empty, and safe error outcomes', async () => {
	const ready = await loadPredictionHistoryState({
		listHistory: async () => ({
			items: [prediction('acct-history')],
			next_cursor: 'history-page-2',
		}),
	}, 'acct-history');
	assert.equal(ready.status, 'ready');
	if (ready.status === 'ready') {
		assert.equal(ready.nextCursor, 'history-page-2');
		assert.deepEqual(ready.pagination, { status: 'idle' });
	}

	const empty = await loadPredictionHistoryState({
		listHistory: async () => ({ items: [] }),
	}, 'acct-history');
	assert.deepEqual(empty, { status: 'empty' });

	const unauthorized = await loadPredictionHistoryState({
		listHistory: async () => {
			throw new PredictionRequestError(401);
		},
	}, 'acct-history');
	assert.deepEqual(unauthorized, {
		status: 'error',
		message: 'Your session has expired. Sign in again to view score history.',
	});

	const unavailable = await loadPredictionHistoryState({
		listHistory: async () => {
			throw new Error('backend detail must stay hidden');
		},
	}, 'acct-history');
	assert.deepEqual(unavailable, {
		status: 'error',
		message: 'Score history is temporarily unavailable. Please try again.',
	});
});

test('next prediction-history page appends revisions and advances the cursor', async () => {
	let requestedAccount = '';
	let requestedCursor: string | undefined;
	const current = {
		status: 'ready' as const,
		items: [
			prediction('acct-history', '2026-08-24T03:00:00Z'),
			prediction('acct-history', '2026-08-24T02:00:00Z'),
		],
		nextCursor: 'history-page-2',
		pagination: { status: 'idle' as const },
	};

	const next = await loadNextPredictionHistoryState({
		listHistory: async (accountExternalId, cursor) => {
			requestedAccount = accountExternalId;
			requestedCursor = cursor;
			return {
				items: [prediction('acct-history', '2026-08-24T01:00:00Z')],
				next_cursor: 'history-page-3',
			};
		},
	}, 'acct-history', current);

	assert.equal(requestedAccount, 'acct-history');
	assert.equal(requestedCursor, 'history-page-2');
	assert.deepEqual(
		next.items.map((item) => item.scored_at),
		[
			'2026-08-24T03:00:00Z',
			'2026-08-24T02:00:00Z',
			'2026-08-24T01:00:00Z',
		],
	);
	assert.equal(next.nextCursor, 'history-page-3');
	assert.deepEqual(next.pagination, { status: 'idle' });
});

test('next prediction-history page exposes completion without another request', async () => {
	let calls = 0;
	const current = {
		status: 'ready' as const,
		items: [prediction('acct-history')],
		pagination: { status: 'idle' as const },
	};

	const unchanged = await loadNextPredictionHistoryState({
		listHistory: async () => {
			calls += 1;
			return { items: [] };
		},
	}, 'acct-history', current);

	assert.equal(calls, 0);
	assert.strictEqual(unchanged, current);
});

test('next prediction-history page preserves revisions and gives safe retry errors', async () => {
	const current = {
		status: 'ready' as const,
		items: [prediction('acct-history')],
		nextCursor: 'history-page-2',
		pagination: { status: 'loading' as const },
	};

	const unavailable = await loadNextPredictionHistoryState({
		listHistory: async () => {
			throw new Error('backend detail must stay hidden');
		},
	}, 'acct-history', current);
	assert.deepEqual(unavailable, {
		...current,
		pagination: {
			status: 'error',
			message: 'More score history is temporarily unavailable. Please try again.',
		},
	});

	const unauthorized = await loadNextPredictionHistoryState({
		listHistory: async () => {
			throw new PredictionRequestError(401);
		},
	}, 'acct-history', current);
	assert.deepEqual(unauthorized, {
		...current,
		pagination: {
			status: 'error',
			message: 'Your session has expired. Sign in again to load more score history.',
		},
	});
});

test('prediction state exposes ready and empty outcomes', async () => {
	const ready = await loadPredictionState({
		listCurrent: async () => ({
			items: [prediction('acct-ready')],
			next_cursor: 'page-2',
		}),
	});
	assert.equal(ready.status, 'ready');
	if (ready.status === 'ready') {
		assert.equal(ready.items[0]?.account_external_id, 'acct-ready');
		assert.equal(ready.nextCursor, 'page-2');
		assert.deepEqual(ready.pagination, { status: 'idle' });
	}

	const empty = await loadPredictionState({
		listCurrent: async () => ({ items: [] }),
	});
	assert.deepEqual(empty, { status: 'empty' });
});

test('next prediction page appends unique accounts and advances the cursor', async () => {
	let requestedCursor: string | undefined;
	const current = {
		status: 'ready' as const,
		items: [prediction('acct-1'), prediction('acct-2')],
		nextCursor: 'page-2',
		pagination: { status: 'idle' as const },
	};

	const next = await loadNextPredictionState({
		listCurrent: async (cursor) => {
			requestedCursor = cursor;
			return {
				items: [prediction('acct-2'), prediction('acct-3')],
				next_cursor: 'page-3',
			};
		},
	}, current);

	assert.equal(requestedCursor, 'page-2');
	assert.equal(next.status, 'ready');
	assert.deepEqual(
		next.items.map((item) => item.account_external_id),
		['acct-1', 'acct-2', 'acct-3'],
	);
	assert.equal(next.nextCursor, 'page-3');
	assert.deepEqual(next.pagination, { status: 'idle' });
});

test('next prediction page exposes completion without another request', async () => {
	let calls = 0;
	const current = {
		status: 'ready' as const,
		items: [prediction('acct-only')],
		pagination: { status: 'idle' as const },
	};

	const unchanged = await loadNextPredictionState({
		listCurrent: async () => {
			calls += 1;
			return { items: [] };
		},
	}, current);

	assert.equal(calls, 0);
	assert.strictEqual(unchanged, current);
});

test('next prediction page preserves results and gives safe retry errors', async () => {
	const current = {
		status: 'ready' as const,
		items: [prediction('acct-kept')],
		nextCursor: 'page-2',
		pagination: { status: 'loading' as const },
	};

	const unavailable = await loadNextPredictionState({
		listCurrent: async () => {
			throw new Error('backend detail must stay hidden');
		},
	}, current);
	assert.deepEqual(unavailable, {
		...current,
		pagination: {
			status: 'error',
			message: 'More predictions are temporarily unavailable. Please try again.',
		},
	});

	const unauthorized = await loadNextPredictionState({
		listCurrent: async () => {
			throw new PredictionRequestError(401);
		},
	}, current);
	assert.deepEqual(unauthorized, {
		...current,
		pagination: {
			status: 'error',
			message: 'Your session has expired. Sign in again to load more predictions.',
		},
	});
});

test('prediction state gives safe session and generic error messages', async () => {
	const unauthorized = await loadPredictionState({
		listCurrent: async () => {
			throw new PredictionRequestError(401);
		},
	});
	assert.deepEqual(unauthorized, {
		status: 'error',
		message: 'Your session has expired. Sign in again to view account risk.',
	});

	const unavailable = await loadPredictionState({
		listCurrent: async () => {
			throw new Error('backend detail must stay hidden');
		},
	});
	assert.deepEqual(unavailable, {
		status: 'error',
		message: 'Predictions are temporarily unavailable. Please try again.',
	});
});

test('prediction client rejects malformed API responses', async () => {
	const client = new PredictionClient(async () => jsonResponse({ items: [{ unexpected: true }] }));
	await assert.rejects(client.listCurrent(), PredictionRequestError);
});

test('risk page renders loading, empty, error and prediction table contracts', () => {
	const component = read('risk/risk.ts');
	const template = read('risk/risk.html');
	assert.match(component, /loadPredictionState/);
	assert.match(component, /status:\s*'loading'/);
	assert.match(template, /state\(\)\.status === 'loading'/);
	assert.match(template, /state\(\)\.status === 'empty'/);
	assert.match(template, /state\(\)\.status === 'error'/);
	assert.match(template, /prediction\.account_external_id/);
	assert.match(template, /prediction\.health_score/);
	assert.match(template, /prediction\.risk_band/);
	assert.match(template, /prediction\.scored_at/);
});

test('risk page exposes accessible Load more, retry, and completion states', () => {
	const component = read('risk/risk.ts');
	const template = read('risk/risk.html');
	assert.match(component, /loadNextPredictionState/);
	assert.match(component, /loadMore/);
	assert.match(component, /isLoadingMore/);
	assert.match(component, /paginationErrorMessage/);
	assert.match(template, /\(click\)="loadMore\(\)"/);
	assert.match(template, /\[disabled\]="isLoadingMore\(\)"/);
	assert.match(template, /Loading more predictions/);
	assert.match(template, /Try loading more again/);
	assert.match(template, /All current predictions are shown/);
});

test('score-history page renders account context and accessible first-page states', () => {
	const component = read('risk/risk-history.ts');
	const template = read('risk/risk-history.html');
	assert.match(component, /loadPredictionHistoryState/);
	assert.match(component, /accountExternalId/);
	assert.match(component, /status:\s*'loading'/);
	assert.match(template, /routerLink="\/risk"/);
	assert.match(template, /state\(\)\.status === 'loading'/);
	assert.match(template, /state\(\)\.status === 'empty'/);
	assert.match(template, /state\(\)\.status === 'error'/);
	assert.match(template, /<caption>[^<]*score history[^<]*<\/caption>/i);
	assert.match(template, /prediction\.health_score/);
	assert.match(template, /prediction\.risk_band/);
	assert.match(template, /prediction\.eligibility/);
	assert.match(template, /prediction\.score_version/);
	assert.match(template, /prediction\.scored_at/);
});

test('score-history page exposes accessible Load more, retry, and completion states', () => {
	const component = read('risk/risk-history.ts');
	const template = read('risk/risk-history.html');
	assert.match(component, /loadNextPredictionHistoryState/);
	assert.match(component, /loadMore/);
	assert.match(component, /isLoadingMore/);
	assert.match(component, /paginationErrorMessage/);
	assert.match(template, /\(click\)="loadMore\(\)"/);
	assert.match(template, /\[disabled\]="isLoadingMore\(\)"/);
	assert.match(template, /Loading more score history/);
	assert.match(template, /Try loading more history again/);
	assert.match(template, /All score history revisions are shown/);
});

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' },
	});
}

function prediction(accountExternalId: string, scoredAt = '2026-08-24T00:00:00Z') {
	return {
		account_external_id: accountExternalId,
		eligibility: 'eligible',
		health_score: 72,
		risk_band: 'medium',
		score_version: 'RULES_BASELINE',
		scored_at: scoredAt,
		explanation_status: 'none',
	};
}
