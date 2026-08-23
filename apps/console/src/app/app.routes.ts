import { Routes } from '@angular/router';

import { OnboardingPage } from './onboarding/onboarding';
import { RiskPage } from './risk/risk';

export const routes: Routes = [
	{ path: '', pathMatch: 'full', redirectTo: 'onboarding' },
	{ path: 'onboarding', component: OnboardingPage },
	{ path: 'risk', component: RiskPage },
];
