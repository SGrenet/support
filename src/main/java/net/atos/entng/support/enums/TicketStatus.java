package net.atos.entng.support.enums;

public enum TicketStatus {
	NEW(1), OPENED(2), RESOLVED(3), CLOSED(4);

	private final int status;

	TicketStatus(int pStatus) {
		this.status = pStatus;
	}
	public int status(){
		return status;
	}
}
