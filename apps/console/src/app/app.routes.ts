import { Routes } from '@angular/router';

import { OnboardingPage } from './onboarding/onboarding';
import { RiskHistoryPage } from './risk/risk-history';
import { RiskPage } from './risk/risk';

export const routes: Routes = [
	{ path: '', pathMatch: 'full', redirectTo: 'onboarding' },
	{ path: 'onboarding', component: OnboardingPage },
	{ path: 'risk/:accountExternalId/history', component: RiskHistoryPage },
	{ path: 'risk', component: RiskPage },
];
