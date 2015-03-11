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
		var apps = _.filter(model.me.apps, function(app) { 
			return app.address && app.name && app.address.length > 0 && app.name.length > 0;
		});
		$scope.apps = orderByFilter(apps, 'name');
		$scope.notFound = false;
		
		$scope.sort = {
			expression : 'modified',
			reverse : true
		};
		
		$scope.filter = {
			status : undefined
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
		
		$scope.escalationStatuses = model.escalationStatuses;
		
		model.isEscalationActivated(function(result){
			if(result && typeof result.isEscalationActivated === 'boolean') {
				$scope.isEscalationActivated = result.isEscalationActivated;
			}
		});
	};

	$scope.filterByStatus = function(item) {
		if(!$scope.filter.status) {
			return true;
		}
		return ($scope.filter.status === item.status);
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
	
	$scope.atLeastOneTicketEscalated = function() {
		return _.some(model.tickets.all, function(ticket){ 
			return ticket.last_issue_update !== null && ticket.last_issue_update !== undefined;
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
		if($scope.userIsLocalAdmin($scope.ticket) === true) {
			$scope.ticket.getBugTrackerIssue();
		}
	};
	
	$scope.viewTicket = function(ticketId) {
		window.location.hash = '/ticket/' + ticketId;
	};
	
	$scope.openViewTicketTemplate = function() {
		template.open('main', 'view-ticket');
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
		$scope.ticket.processing = true;
		
		if (!$scope.ticket.subject || $scope.ticket.subject.trim().length === 0){
			notify.error('support.ticket.validation.error.subject.is.empty');
			$scope.ticket.processing = false;
			return;
		}
		if( $scope.ticket.subject.length > 255) {
			notify.error('support.ticket.validation.error.subject.too.long');
			$scope.ticket.processing = false;
			return;
		}
		
		if (!$scope.ticket.description || $scope.ticket.description.trim().length === 0){
			notify.error('support.ticket.validation.error.description.is.empty');
			$scope.ticket.processing = false;
			return;
		}
		
		if(this.hasDuplicateInNewAttachments() === true) {
			notify.error('support.ticket.validation.error.duplicate.in.new.attachments');
			$scope.ticket.processing = false;
			return;
		}
		
		$scope.createProtectedCopies($scope.ticket, true, function() {
			template.open('main', 'list-tickets');
			$scope.ticket.processing = false;
			$scope.ticket.createTicket($scope.ticket, function() {
				$scope.ticket.newAttachments = [];
				notify.info('support.ticket.has.been.created');
			});
		});
	}.bind(this);
	
	$scope.cancelCreateTicket = function() {
		template.open('main', 'list-tickets');
	};
	
	/*
	 * Create a "protected" copy for each "non protected" attachment.
	 * ("Non protected" attachments cannot be seen by everybody, whereas "protected" attachments can)
	 */
	$scope.createProtectedCopies = function(pTicket, pIsCreateTicket, pCallback) {
		
		if(!pTicket.newAttachments || pTicket.newAttachments.length === 0) {
			if(typeof pCallback === 'function'){
				pCallback();
			}
		}
		else {
			var nonProtectedAttachments = _.filter(pTicket.newAttachments, 
					function(attachment) { return attachment.protected !== true; });
			var remainingAttachments = nonProtectedAttachments.length;
			
			// Function factory, to ensure anAttachment has the proper value
			var makeCallbackFunction = function (pAttachment) {
				var anAttachment = pAttachment;
				return function(result) {
					// Exemple of result : {_id: "db1f060a-5c0e-45fa-8318-2d8b33873747", status: "ok"}
					
					if(result && result.status === "ok") {
						console.log("createProtectedCopy OK for attachment "+anAttachment._id + ". Id of protected copy is:"+result._id);
						remainingAttachments = remainingAttachments - 1;
						
						// replace id of "non protected" attachment by the id of its "protected copy"
						pTicket.newAttachments = _.map(pTicket.newAttachments, 
							function(attachment) {
								if(anAttachment._id === attachment._id) {
									attachment._id = result._id;
								}
								return attachment;
							}
						);
						if(remainingAttachments === 0) {
							console.log("createProtectedCopy OK for all attachments");
							if(typeof pCallback === 'function'){
								pCallback();
							}
						}
					}
					else {
						if(pIsCreateTicket === true) {
							notify.error('support.attachment.processing.failed.ticket.cannot.be.created');
							pTicket.processing = false;
						}
						else {
							notify.error('support.attachment.processing.failed.ticket.cannot.be.updated');
							pTicket.processing = false;
						}
						return;
					}
					
				};
			};
			
			if(nonProtectedAttachments && nonProtectedAttachments.length > 0) {
				for (var i=0; i < nonProtectedAttachments.length; i++) {
					Behaviours.applicationsBehaviours.workspace.protectedDuplicate(nonProtectedAttachments[i], 
							makeCallbackFunction(nonProtectedAttachments[i]));
				}
			}
			else {
				if(typeof pCallback === 'function'){
					pCallback();
				}
			}
		}
	};
	
	// Update ticket
	$scope.editTicket = function() {
		$scope.editedTicket = _.find(model.tickets.all, function(ticket){
			return ticket.id === $scope.ticket.id;
		});
		template.open('main', 'edit-ticket');
	};
	
	$scope.updateTicket = function() {
		$scope.editedTicket.processing = true;
		
		if (!$scope.editedTicket.subject || $scope.editedTicket.subject.trim().length === 0){
			notify.error('support.ticket.validation.error.subject.is.empty');
			$scope.editedTicket.processing = false;
			return;
		}
		if( $scope.editedTicket.subject.length > 255) {
			notify.error('support.ticket.validation.error.subject.too.long');
			$scope.editedTicket.processing = false;
			return;
		}
		
		if (!$scope.editedTicket.description || $scope.editedTicket.description.trim().length === 0){
			notify.error('support.ticket.validation.error.description.is.empty');
			$scope.editedTicket.processing = false;
			return;
		}
		
		if(this.hasDuplicateInNewAttachments() === true) {
			notify.error('support.ticket.validation.error.duplicate.in.new.attachments');
			$scope.editedTicket.processing = false;
			return;
		}
		
		// check that the "new" attachments have not already been saved for the current ticket 
		if($scope.ticket.newAttachments && $scope.ticket.newAttachments.length > 0) {
			var attachmentsIds = $scope.ticket.attachments.pluck('document_id');
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
				$scope.editedTicket.processing = false;
				return;
			}
		}
		
		$scope.createProtectedCopies($scope.editedTicket, false, function() {
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
				$scope.ticket.processing = false;
				
				template.open('main', 'view-ticket');
			});
		});

	}.bind(this);
	
	$scope.cancelEditTicket = function() {
		$scope.editedTicket = new Ticket();
		template.open('main', 'view-ticket');
	};
	
	$scope.isCreatingOrEditingOrViewingEscalatedTicket = function() {
		return (template.contains('main', 'create-ticket') || 
				template.contains('main', 'edit-ticket') ||
				$scope.isViewingEscalatedTicket());
	};
	
	$scope.isViewingEscalatedTicket = function() {
		return template.contains('main', 'view-bugtracker-issue');
	};
	
	// Functions to escalate tickets or process escalated tickets
	$scope.escalateTicket = function() {
		$scope.ticket.escalation_status = model.escalationStatuses.IN_PROGRESS;
		if($scope.ticket.status !== model.ticketStatusEnum.NEW && $scope.ticket.status !== model.ticketStatusEnum.OPENED) {
			notify.error('support.ticket.escalation.not.allowed.for.given.status');
			$scope.ticket.escalation_status = model.escalationStatuses.NOT_DONE;
			return;	
		}
		
		var successCallback = function() {
			notify.info('support.ticket.escalation.successful');
		};
		var e500Callback = function() {
			notify.error('support.ticket.escalation.failed');
		};
		var e400Callback = function(result) {
			if(result && result.error) {
				notify.error(result.error);
			}
			else {
				notify.error('support.error.escalation.conflict');
			}
		};
		
		$scope.ticket.escalateTicket(successCallback, e500Callback, e400Callback);
	};
	
	$scope.openBugTrackerIssue = function() {
		template.open('main', 'view-bugtracker-issue');
	};
	
	$scope.editIssue = function() {
		$scope.ticket.issue.showEditForm = true;
	};
	
	$scope.cancelEditIssue = function() {
		$scope.ticket.issue.showEditForm = false;
		$scope.ticket.issue.newComment = '';
	};
	
	$scope.updateIssue = function() {
		$scope.ticket.issue.processing = true;
		
		if (!$scope.ticket.issue.newComment || $scope.ticket.issue.newComment.trim().length === 0){
			notify.error('support.issue.validation.error.comment.is.empty');
			$scope.ticket.issue.processing = false;
			return;
		}
		
		var successCallback = function() {
			$scope.ticket.issue.showEditForm = false;
			$scope.ticket.issue.processing = false;
			notify.info('support.comment.issue.successful');
		};
		
		var errorCallback = function(error) {
			notify.error(error);
			$scope.ticket.issue.processing = false;
		};
		
		$scope.ticket.commentIssue(successCallback, errorCallback);
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
	
	
	$scope.userIsLocalAdmin = function(ticket){
		var isLocalAdmin = (model.me.functions && 
				model.me.functions.ADMIN_LOCAL && 
				model.me.functions.ADMIN_LOCAL.scope);
		
		if(ticket && ticket.school_id) {
			// if parameter "ticket" is supplied, check that current user is local administrator for the ticket's school
			return 	isLocalAdmin && _.contains(model.me.functions.ADMIN_LOCAL.scope, ticket.school_id);
		}
		return isLocalAdmin;
	};
	
	
	this.initialize();
}