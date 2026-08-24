import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';

import {
	PredictionClient,
	PredictionViewState,
	loadPredictionState,
} from './prediction-client';

@Component({
	selector: 'app-risk',
	imports: [DatePipe],
	templateUrl: './risk.html',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskPage implements OnInit {
	readonly state = signal<PredictionViewState>({ status: 'loading' });
	protected readonly items = computed(() => {
		const state = this.state();
		return state.status === 'ready' ? state.items : [];
	});
	protected readonly errorMessage = computed(() => {
		const state = this.state();
		return state.status === 'error' ? state.message : '';
	});

	private readonly predictions = new PredictionClient();

	ngOnInit(): void {
		void this.load();
	}

	protected retry(): void {
		void this.load();
	}

	private async load(): Promise<void> {
		this.state.set({ status: 'loading' });
		this.state.set(await loadPredictionState(this.predictions));
	}
}
