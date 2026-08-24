import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

import {
	PredictionClient,
	PredictionRequestError,
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

test('prediction state exposes ready and empty outcomes', async () => {
	const ready = await loadPredictionState({
		listCurrent: async () => ({ items: [prediction('acct-ready')] }),
	});
	assert.equal(ready.status, 'ready');
	if (ready.status === 'ready') {
		assert.equal(ready.items[0]?.account_external_id, 'acct-ready');
	}

	const empty = await loadPredictionState({
		listCurrent: async () => ({ items: [] }),
	});
	assert.deepEqual(empty, { status: 'empty' });
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

function jsonResponse(body: unknown, status = 200): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' },
	});
}

function prediction(accountExternalId: string) {
	return {
		account_external_id: accountExternalId,
		eligibility: 'eligible',
		health_score: 72,
		risk_band: 'medium',
		score_version: 'RULES_BASELINE',
		scored_at: '2026-08-24T00:00:00Z',
		explanation_status: 'none',
	};
}
