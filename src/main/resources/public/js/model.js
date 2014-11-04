// Enum based on the following article : https://stijndewitt.wordpress.com/2014/01/26/enums-in-javascript/
model.ticketStatusEnum = {
	NEW: 1,
	OPENED: 2,
	RESOLVED: 3,
	ClOSED: 4,
	properties: {
	    1: {i18n: "support.ticket.status.new", value: 1},
	    2: {i18n: "support.ticket.status.opened", value: 2},
	    3: {i18n: "support.ticket.status.resolved", value: 3},
	    4: {i18n: "support.ticket.status.closed", value: 4}
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

Ticket.prototype.updateTicket = function(data, callback) {
	http().putJson('/support/ticket/' + this.id, data).done(function(result){
		this.updateData(result);
		this.trigger('change');
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
	if(this.status !== undefined) {
		json.status = this.status;
	}
	if(this.newComment !== undefined) {
		json.newComment = this.newComment;
	}
	
	return json;
};

Ticket.prototype.getComments = function(callback) {
	http().get('/support/ticket/' + this.id + '/comments').done(function(result){
		if(result.length > 0) {
			this.comments.load(result);
		}
		if(typeof callback === 'function'){
			callback();
		}
	}.bind(this));
};


model.build = function() {
	model.me.workflow.load(['support']);
	this.makeModels([ Ticket, Comment ]);

	this.collection(Ticket, {
		sync : function() {
			http().get('/support/tickets').done(function(tickets) {
				this.load(tickets);
			}.bind(this));
		}, 
		behaviours: 'support'
	});
};