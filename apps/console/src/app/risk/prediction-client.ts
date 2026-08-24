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
	| { status: 'ready'; items: readonly Prediction[] }
	| { status: 'error'; message: string };

export interface PredictionSource {
	listCurrent(): Promise<PredictionListResponse>;
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

export class PredictionClient implements PredictionSource {
	private readonly fetcher: FetchLike;

	constructor(fetcher: FetchLike = (url, init) => globalThis.fetch(url, init)) {
		this.fetcher = fetcher;
	}

	async listCurrent(): Promise<PredictionListResponse> {
		const response = await this.fetcher('/v1/predictions?limit=50', {
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
	try {
		const response = await source.listCurrent();
		return response.items.length === 0
			? { status: 'empty' }
			: { status: 'ready', items: response.items };
	}
	catch (error) {
		if (error instanceof PredictionRequestError && error.status === 401) {
			return {
				status: 'error',
				message: 'Your session has expired. Sign in again to view account risk.',
			};
		}
		return {
			status: 'error',
			message: 'Predictions are temporarily unavailable. Please try again.',
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
