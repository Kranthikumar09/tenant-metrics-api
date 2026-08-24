export interface Prediction {
	account_external_id: string;
	eligibility: string;
	health_score?: number;
	risk_band?: string;
	risk_probability?: number | null;
	score_version: string;
	feature_version?: string;
	drivers?: readonly unknown[];
	scored_at: string;
	freshness_seconds?: number;
	explanation_status: string;
}

export interface PredictionListResponse {
	items: readonly Prediction[];
	next_cursor?: string;
}

export type PredictionViewState =
	| { status: 'loading' }
	| { status: 'empty' }
	| {
		status: 'ready';
		items: readonly Prediction[];
		nextCursor?: string;
		pagination:
			| { status: 'idle' }
			| { status: 'loading' }
			| { status: 'error'; message: string };
	}
	| { status: 'error'; message: string };

export interface PredictionSource {
	listCurrent(cursor?: string): Promise<PredictionListResponse>;
}

export interface PredictionHistorySource {
	listHistory(accountExternalId: string, cursor?: string): Promise<PredictionListResponse>;
}

export type FetchLike = (url: string, init: RequestInit) => Promise<Response>;

export class PredictionRequestError extends Error {
	readonly status: number;

	constructor(status: number) {
		super('Prediction request failed');
		this.name = 'PredictionRequestError';
		this.status = status;
	}
}

export class PredictionClient implements PredictionSource, PredictionHistorySource {
	private readonly fetcher: FetchLike;

	constructor(fetcher: FetchLike = (url, init) => globalThis.fetch(url, init)) {
		this.fetcher = fetcher;
	}

	async listCurrent(cursor?: string): Promise<PredictionListResponse> {
		return this.list(`/v1/predictions?limit=50${pageCursorQuery(cursor)}`);
	}

	async listHistory(accountExternalId: string, cursor?: string): Promise<PredictionListResponse> {
		if (accountExternalId.length === 0) {
			throw new PredictionRequestError(400);
		}
		const accountPath = encodeURIComponent(accountExternalId);
		return this.list(
			`/v1/accounts/${accountPath}/prediction-history?limit=50${pageCursorQuery(cursor)}`,
		);
	}

	private async list(url: string): Promise<PredictionListResponse> {
		const response = await this.fetcher(url, {
			method: 'GET',
			credentials: 'same-origin',
			cache: 'no-store',
			headers: { Accept: 'application/json' },
		});
		if (!response.ok) {
			throw new PredictionRequestError(response.status);
		}

		let body: unknown;
		try {
			body = await response.json();
		}
		catch {
			throw new PredictionRequestError(502);
		}
		if (!isPredictionList(body)) {
			throw new PredictionRequestError(502);
		}
		return body;
	}
}

export async function loadPredictionState(source: PredictionSource): Promise<PredictionViewState> {
	return loadState(
		() => source.listCurrent(),
		'Your session has expired. Sign in again to view account risk.',
		'Predictions are temporarily unavailable. Please try again.',
	);
}

export async function loadPredictionHistoryState(
	source: PredictionHistorySource,
	accountExternalId: string,
): Promise<PredictionViewState> {
	return loadState(
		() => source.listHistory(accountExternalId),
		'Your session has expired. Sign in again to view score history.',
		'Score history is temporarily unavailable. Please try again.',
	);
}

export async function loadNextPredictionState(
	source: PredictionSource,
	current: Extract<PredictionViewState, { status: 'ready' }>,
): Promise<Extract<PredictionViewState, { status: 'ready' }>> {
	return loadNextState(
		current,
		(cursor) => source.listCurrent(cursor),
		(existing, incoming) => {
			const seenAccounts = new Set(existing.map((item) => item.account_external_id));
			const uniqueItems = incoming.filter((item) => {
				if (seenAccounts.has(item.account_external_id)) {
					return false;
				}
				seenAccounts.add(item.account_external_id);
				return true;
			});
			return [...existing, ...uniqueItems];
		},
		'Your session has expired. Sign in again to load more predictions.',
		'More predictions are temporarily unavailable. Please try again.',
	);
}

export async function loadNextPredictionHistoryState(
	source: PredictionHistorySource,
	accountExternalId: string,
	current: Extract<PredictionViewState, { status: 'ready' }>,
): Promise<Extract<PredictionViewState, { status: 'ready' }>> {
	return loadNextState(
		current,
		(cursor) => source.listHistory(accountExternalId, cursor),
		(existing, incoming) => [...existing, ...incoming],
		'Your session has expired. Sign in again to load more score history.',
		'More score history is temporarily unavailable. Please try again.',
	);
}

async function loadNextState(
	current: Extract<PredictionViewState, { status: 'ready' }>,
	request: (cursor: string) => Promise<PredictionListResponse>,
	mergeItems: (
		existing: readonly Prediction[],
		incoming: readonly Prediction[],
	) => readonly Prediction[],
	unauthorizedMessage: string,
	unavailableMessage: string,
): Promise<Extract<PredictionViewState, { status: 'ready' }>> {
	if (current.nextCursor === undefined) {
		return current;
	}

	try {
		const response = await request(current.nextCursor);
		return {
			status: 'ready',
			items: mergeItems(current.items, response.items),
			...(response.next_cursor === undefined
				? {}
				: { nextCursor: response.next_cursor }),
			pagination: { status: 'idle' },
		};
	}
	catch (error) {
		const message = error instanceof PredictionRequestError && error.status === 401
			? unauthorizedMessage
			: unavailableMessage;
		return {
			...current,
			pagination: { status: 'error', message },
		};
	}
}

async function loadState(
	request: () => Promise<PredictionListResponse>,
	unauthorizedMessage: string,
	unavailableMessage: string,
): Promise<PredictionViewState> {
	try {
		const response = await request();
		return response.items.length === 0
			? { status: 'empty' }
			: {
				status: 'ready',
				items: response.items,
				...(response.next_cursor === undefined
					? {}
					: { nextCursor: response.next_cursor }),
				pagination: { status: 'idle' },
			};
	}
	catch (error) {
		if (error instanceof PredictionRequestError && error.status === 401) {
			return {
				status: 'error',
				message: unauthorizedMessage,
			};
		}
		return {
			status: 'error',
			message: unavailableMessage,
		};
	}
}

function isPredictionList(value: unknown): value is PredictionListResponse {
	if (!isRecord(value) || !Array.isArray(value['items'])) {
		return false;
	}
	if (value['next_cursor'] !== undefined && typeof value['next_cursor'] !== 'string') {
		return false;
	}
	return value['items'].every(isPrediction);
}

function isPrediction(value: unknown): value is Prediction {
	return isRecord(value)
		&& typeof value['account_external_id'] === 'string'
		&& typeof value['eligibility'] === 'string'
		&& optionalNumber(value['health_score'])
		&& optionalString(value['risk_band'])
		&& typeof value['score_version'] === 'string'
		&& typeof value['scored_at'] === 'string'
		&& typeof value['explanation_status'] === 'string';
}

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === 'object' && value !== null;
}

function optionalString(value: unknown): boolean {
	return value === undefined || typeof value === 'string';
}

function optionalNumber(value: unknown): boolean {
	return value === undefined || typeof value === 'number';
}

function pageCursorQuery(cursor?: string): string {
	if (cursor === undefined) {
		return '';
	}
	if (cursor.length === 0) {
		throw new PredictionRequestError(400);
	}
	return `&cursor=${encodeURIComponent(cursor)}`;
}
