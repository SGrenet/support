routes.define(function($routeProvider){
    $routeProvider
      .when('/ticket/:ticketId', {
        action: 'displayTicket'
      })
      .when('/list-tickets', {
		action: 'listTickets'
      })
      .otherwise({
    	redirectTo: '/list-tickets'
      });
});

function SupportController($scope, template, model, route, $location, orderByFilter){

	route({
		displayTicket: function(params) {
			$scope.displayTicket(params.ticketId);
		},
		listTickets: function() {
        	$scope.registerViewTicketListEvent();	    
        }
	});
	
	this.initialize = function() {
		$scope.template = template;
		$scope.me = model.me;
		
		$scope.tickets = model.tickets;
		$scope.apps = orderByFilter(model.me.apps, 'name');
		$scope.notFound = false;
		
		$scope.sort = {
			expression : 'modified',
			reverse : true
		};
		
		$scope.schools = [];
		for (var i=0; i < model.me.structures.length; i++) {
			$scope.schools.push({id: model.me.structures[i], name: model.me.structureNames[i]});
		}
		
		// Clone status enum and add i18n value
		var statusEnum = JSON.parse(JSON.stringify(model.ticketStatusEnum));
		for (var status in statusEnum.properties) {
			if (statusEnum.properties.hasOwnProperty(status)) {
				statusEnum.properties[status].i18nValue = $scope.getStatusLabel(statusEnum.properties[status].value);			
			}
		}
		$scope.statuses = statusEnum.properties;
	};

	// Sort
	$scope.sortCategoryFunction = function(ticket) {
		return $scope.getCategoryLabel(ticket.category);
	};
	
	$scope.switchSortBy = function(expression) {
		if (expression === $scope.sort.expression) {
			$scope.sort.reverse = ! $scope.sort.reverse;
		}
		else {
			$scope.sort.expression = expression;
			$scope.sort.reverse = false;
		}
	};
	
	// View tickets
	$scope.displayTicketList = function() {
		$scope.ticket = new Ticket();
		$scope.registerViewTicketListEvent();
    	model.tickets.sync();
	};

	$scope.registerViewTicketListEvent = function() {
    	model.tickets.one('sync', function() {
    		window.location.hash = '';
			template.open('main', 'list-tickets');
    	});		
	};
	
	$scope.displayTicket = function(ticketId) {
		if(model.tickets.all === undefined || model.tickets.isEmpty()) {
	    	model.tickets.one('sync', function() {
	    		$scope.openTicket(ticketId);
	    	});
	    	model.tickets.sync();
		}
		else {
			$scope.openTicket(ticketId);
		}
	};
	
	$scope.openTicket = function(ticketId) {
		var id = parseInt(ticketId,10);
		$scope.ticket = _.find(model.tickets.all, function(ticket){
			return ticket.id === id;
		});
    	if(!$scope.ticket) {
    		$scope.notFound = true;
    		return;
    	}
		template.open('main', 'view-ticket');
		$scope.ticket.getAttachments();
		$scope.ticket.getComments();
	};
	
	$scope.viewTicket = function(ticketId) {
		window.location.hash = '/ticket/' + ticketId;
	};
	
	// Create ticket
	$scope.newTicket = function() {
		$scope.ticket = new Ticket();
		template.open('main', 'create-ticket');
	};
	
	this.hasDuplicateInNewAttachments = function() {
		// each attachmentId must appear only once. Return true if there are duplicates, false otherwise
		return _.chain($scope.ticket.newAttachments)
			.countBy(function(attachment) { return attachment._id; })
			.values()
			.some(function(count){ return count > 1; })
			.value();
	};
	
	$scope.createTicket = function() {
		if (!$scope.ticket.subject || $scope.ticket.subject.trim().length === 0){
			notify.error('support.ticket.validation.error.subject.is.empty');
			return;
		}
		if( $scope.ticket.subject.length > 255) {
			notify.error('support.ticket.validation.error.subject.too.long');
			return;
		}
		
		if (!$scope.ticket.description || $scope.ticket.description.trim().length === 0){
			notify.error('support.ticket.validation.error.description.is.empty');
			return;
		}
		
		if(this.hasDuplicateInNewAttachments() === true) {
			notify.error('support.ticket.validation.error.duplicate.in.new.attachments');
			return;
		}
		
		template.open('main', 'list-tickets');
		$scope.ticket.createTicket($scope.ticket, function() {
			$scope.ticket.newAttachments = [];
			notify.info('support.ticket.has.been.created');
		});
	}.bind(this);
	
	$scope.cancelCreateTicket = function() {
		template.open('main', 'list-tickets');
	};
	
	// Update ticket
	$scope.editTicket = function() {
		$scope.editedTicket = _.find(model.tickets.all, function(ticket){
			return ticket.id === $scope.ticket.id;
		});
		template.open('main', 'edit-ticket');
	};
	
	$scope.updateTicket = function() {
		if (!$scope.editedTicket.subject || $scope.editedTicket.subject.trim().length === 0){
			notify.error('support.ticket.validation.error.subject.is.empty');
			return;
		}
		if( $scope.editedTicket.subject.length > 255) {
			notify.error('support.ticket.validation.error.subject.too.long');
			return;
		}
		
		if (!$scope.editedTicket.description || $scope.editedTicket.description.trim().length === 0){
			notify.error('support.ticket.validation.error.description.is.empty');
			return;
		}
		
		if(this.hasDuplicateInNewAttachments() === true) {
			notify.error('support.ticket.validation.error.duplicate.in.new.attachments');
			return;
		}
		
		// check that the "new" attachments have not already been saved for the current ticket 
		if($scope.ticket.newAttachments && $scope.ticket.newAttachments.length > 0) {
			var attachmentsIds = $scope.ticket.attachments.pluck('gridfs_id');
			var newAttachmentsInDuplicate = [];
			
			for (var i=0; i < $scope.ticket.newAttachments.length; i++) {
				if(_.contains(attachmentsIds, $scope.ticket.newAttachments[i]._id)) {
					newAttachmentsInDuplicate.push({
						id: $scope.ticket.newAttachments[i]._id, 
						name: $scope.ticket.newAttachments[i].title
					});
				}
			}
			
			if(newAttachmentsInDuplicate.length > 0) {
				if(newAttachmentsInDuplicate.length === 1) {
					notify.error(lang.translate('support.ticket.validation.error.attachment') + 
							newAttachmentsInDuplicate[0].name + 
							lang.translate('support.ticket.validation.error.already.linked.to.ticket'));
				}
				else {
					notify.error(lang.translate('support.ticket.validation.error.attachments.already.linked.to.ticket') + 
							_.pluck(newAttachmentsInDuplicate,'name').join(", "));
				}
				return;
			}
		}

		$scope.ticket = $scope.editedTicket;
		$scope.ticket.updateTicket($scope.ticket, function() {
			if($scope.ticket.newAttachments && $scope.ticket.newAttachments.length > 0) {
				$scope.ticket.getAttachments();
			}
			$scope.ticket.newAttachments = [];
			
			if($scope.ticket.newComment && $scope.ticket.newComment.length > 0) {
				$scope.ticket.getComments();
			}
			$scope.ticket.newComment = '';
			
			template.open('main', 'view-ticket');
		});
	}.bind(this);
	
	$scope.cancelEditTicket = function() {
		$scope.editedTicket = new Ticket();
		template.open('main', 'view-ticket');
	};
	
	$scope.isCreatingOrEditing = function() {
		return (template.contains('main', 'create-ticket') || 
				template.contains('main', 'edit-ticket'));
	};
	
	// Date functions
	$scope.formatDate = function(date) {
		return $scope.formatMoment(moment(date));
	};

	$scope.formatMoment = function(moment) {
		return moment.lang('fr').format('DD/MM/YYYY H:mm');
	};
	
	// Functions to display proper label
	$scope.getStatusLabel = function(status) {
		if(model.ticketStatusEnum.properties[status] !== undefined) {
			var key = model.ticketStatusEnum.properties[status].i18n;
			return lang.translate(key);
		}
		return undefined;
	};
	
	$scope.getCategoryLabel = function(appAddress) {
		var app = _.find(model.me.apps, function(app){
			return app.address === appAddress;
		});
		var label = (app !== undefined) ? app.name : undefined;
		return label;
	};
	
	$scope.getSchoolName = function(schoolId) {
		var school = _.find($scope.schools, function(school){
			return school.id === schoolId;
		});
		var schoolName = (school !== undefined) ? school.name : undefined;
		return schoolName;
	};
	
	
	this.initialize();
}