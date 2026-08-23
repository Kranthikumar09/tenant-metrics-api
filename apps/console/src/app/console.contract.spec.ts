import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

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
