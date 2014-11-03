// Enum based on the following article : https://stijndewitt.wordpress.com/2014/01/26/enums-in-javascript/
model.ticketStatusEnum = {
	NEW: 1,
	OPENED: 2,
	RESOLVED: 3,
	ClOSED: 4,
	properties: {
	    1: {i18n: "support.ticket.status.new"},
	    2: {i18n: "support.ticket.status.opened",},
	    3: {i18n: "support.ticket.status.resolved"},
	    4: {i18n: "support.ticket.status.closed"}
	}
};

if (Object.freeze) {
	Object.freeze(model.ticketStatusEnum);	
}


function Comment(){

}

function Ticket(){
	this.collection(Comment);
}

Ticket.prototype.createTicket = function(data, callback) {
	http().postJson('/support/ticket', data).done(function(result){
		this.updateData(result);
		model.tickets.push(this);
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};

Ticket.prototype.toJSON = function() {
	var json = {
		    subject : this.subject,
		    description : this.description,
		    category : this.category,
		    school_id : model.me.structures[0] // TODO : à modifier
	};
	
	return json;
};


model.build = function() {
	model.me.workflow.load(['support']);
	this.makeModels([ Ticket ]);

	this.collection(Ticket, {
		sync : function() {
			http().get('/support/tickets/mine').done(function(tickets) {
				this.load(tickets);
			}.bind(this));
		}, 
		behaviours: 'support'
	});
};