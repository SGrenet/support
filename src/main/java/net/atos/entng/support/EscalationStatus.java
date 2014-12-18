package net.atos.entng.support;

public enum EscalationStatus {
	NOT_DONE(1), IN_PROGRESS(2), SUCCESSFUL(3), FAILED(4);

	private final int status;

	EscalationStatus(int pStatus) {
		this.status = pStatus;
	}

	public int status() {
		return status;
	}
}
