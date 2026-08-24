import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { requestLogout } from './auth/logout';
import type { LogoutResult } from './auth/logout';

type LogoutStatus = { status: 'idle' | 'pending' } | LogoutResult;

@Component({
	selector: 'app-root',
	imports: [RouterLink, RouterLinkActive, RouterOutlet],
	templateUrl: './app.html',
})
export class App {
	private readonly http = inject(HttpClient);
	private readonly router = inject(Router);

	protected readonly logoutStatus = signal<LogoutStatus>({ status: 'idle' });

	protected async signOut(): Promise<void> {
		if (this.logoutStatus().status === 'pending') {
			return;
		}

		this.logoutStatus.set({ status: 'pending' });
		const result = await requestLogout(() =>
			firstValueFrom(
				this.http.post<void>('/logout', null, { withCredentials: true }),
			),
		);
		this.logoutStatus.set(result);

		if (result.status === 'signed-out') {
			await this.router.navigateByUrl('/onboarding');
		}
	}
}
