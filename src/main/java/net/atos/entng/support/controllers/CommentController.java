package net.atos.entng.support.controllers;

import net.atos.entng.support.services.CommentService;
import net.atos.entng.support.services.CommentServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;


public class CommentController extends ControllerHelper {

	private CommentService commentService;

	public CommentController() {
		commentService = new CommentServiceSqlImpl();
	}


}
