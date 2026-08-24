import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { bootstrapApplication } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { App } from './app/app';
import { routes } from './app/app.routes';

bootstrapApplication(App, {
	providers: [
		provideRouter(routes),
		provideHttpClient(withXsrfConfiguration({
			cookieName: 'XSRF-TOKEN',
			headerName: 'X-XSRF-TOKEN',
		})),
	],
}).catch((error) => {
	console.error(error);
});
