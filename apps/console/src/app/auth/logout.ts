export type LogoutResult =
	| { status: 'signed-out' }
	| { status: 'error'; message: string };

export async function requestLogout(
	request: () => Promise<unknown>,
): Promise<LogoutResult> {
	try {
		await request();
		return { status: 'signed-out' };
	} catch {
		return {
			status: 'error',
			message: 'We could not sign you out. Please try again.',
		};
	}
}
