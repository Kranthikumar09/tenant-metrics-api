import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import {
	PredictionClient,
	PredictionViewState,
	loadNextPredictionHistoryState,
	loadPredictionHistoryState,
} from './prediction-client';

@Component({
	selector: 'app-risk-history',
	imports: [DatePipe, RouterLink],
	templateUrl: './risk-history.html',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskHistoryPage implements OnInit {
	private readonly route = inject(ActivatedRoute);
	private readonly predictions = new PredictionClient();

	protected readonly accountExternalId =
		this.route.snapshot.paramMap.get('accountExternalId') ?? '';
	readonly state = signal<PredictionViewState>({ status: 'loading' });
	protected readonly items = computed(() => {
		const state = this.state();
		return state.status === 'ready' ? state.items : [];
	});
	protected readonly errorMessage = computed(() => {
		const state = this.state();
		return state.status === 'error' ? state.message : '';
	});
	protected readonly hasMore = computed(() => {
		const state = this.state();
		return state.status === 'ready' && state.nextCursor !== undefined;
	});
	protected readonly isLoadingMore = computed(() => {
		const state = this.state();
		return state.status === 'ready' && state.pagination.status === 'loading';
	});
	protected readonly paginationErrorMessage = computed(() => {
		const state = this.state();
		return state.status === 'ready' && state.pagination.status === 'error'
			? state.pagination.message
			: '';
	});

	ngOnInit(): void {
		void this.load();
	}

	protected retry(): void {
		void this.load();
	}

	protected loadMore(): void {
		const current = this.state();
		if (current.status !== 'ready'
			|| current.nextCursor === undefined
			|| current.pagination.status === 'loading') {
			return;
		}

		this.state.set({
			...current,
			pagination: { status: 'loading' },
		});
		void this.loadNextPage(current);
	}

	private async load(): Promise<void> {
		this.state.set({ status: 'loading' });
		this.state.set(await loadPredictionHistoryState(
			this.predictions,
			this.accountExternalId,
		));
	}

	private async loadNextPage(
		current: Extract<PredictionViewState, { status: 'ready' }>,
	): Promise<void> {
		this.state.set(await loadNextPredictionHistoryState(
			this.predictions,
			this.accountExternalId,
			current,
		));
	}
}
