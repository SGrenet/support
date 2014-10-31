routes.define(function($routeProvider){
    $routeProvider
      .when('/ticket/:ticketId', {
        action: 'viewTicket'
      })
      .otherwise({
    	  action: 'defaultView'
      });
});

function SupportController($scope, template, model, route, $location){

	route({
		viewTicket: function(params) {
			$scope.viewTicket(params.ticketId);
		},
        defaultView: function() {
        	$scope.registerViewTicketListEvent();	    
        }
	});
	
	this.initialize = function() {
		$scope.template = template;
		$scope.me = model.me;
		
		$scope.tickets = model.tickets;
		$scope.categories = model.me.apps;
	};
	
	$scope.displayTicketList = function() {
		$scope.registerViewTicketListEvent();
    	model.tickets.sync();
	};

	$scope.registerViewTicketListEvent = function() {
    	model.tickets.one('sync', function() {
	    	window.location.hash = '';
			template.open('main', 'list-tickets');
    	});		
	};
	
	$scope.viewTicket = function(ticketId) {
		$scope.ticket = _.find(model.tickets.all, function(ticket){
			return ticket.id === ticketId;
		});
		template.open('main', 'view-ticket');
	};
	
	$scope.newTicket = function() {
		$scope.ticket = new Ticket();
		template.open('main', 'create-ticket');
	};
	
	$scope.createTicket = function() {
		template.open('main', 'view-ticket');
		$scope.ticket.createTicket($scope.ticket);
	};
	
	// Date functions
	$scope.formatDate = function(date) {
		return $scope.formatMoment(moment(date));
	};

	$scope.formatMoment = function(moment) {
		return moment.lang('fr').format('D/MM/YYYY H:mm');
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
	
	
	this.initialize();
}