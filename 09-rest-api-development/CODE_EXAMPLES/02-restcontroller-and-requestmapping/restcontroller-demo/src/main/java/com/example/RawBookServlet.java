package com.example;

import java.io.IOException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// What BookController.one(...) looks like with no Spring MVC: parse the path,
// convert the id, pick the status, set the content type, write the JSON by hand.
public class RawBookServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo(); // "/1" for /raw/books/1
        long id;
        try {
            id = Long.parseLong(path.substring(1));
        } catch (RuntimeException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "id must be a number");
            return;
        }
        if (id != 1L) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        resp.setContentType("application/json");
        resp.getWriter().write("{\"id\":1,\"title\":\"Effective Java\",\"author\":\"Bloch\"}");
    }
}
