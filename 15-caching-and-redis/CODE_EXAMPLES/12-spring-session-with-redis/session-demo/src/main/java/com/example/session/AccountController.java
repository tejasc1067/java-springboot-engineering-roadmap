package com.example.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** A deliberately tiny "login": the lesson is where the session lives, not how the password is checked (module 12). */
@RestController
class AccountController {

    private final String instanceName;

    AccountController(@Value("${app.instance-name}") String instanceName) {
        this.instanceName = instanceName;
    }

    @PostMapping("/login")
    String login(@RequestParam String user, HttpSession session) {
        session.setAttribute("user", user);
        return "logged in as " + user + " on " + instanceName;
    }

    @GetMapping("/me")
    ResponseEntity<String> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);   // false: don't create one if there isn't any
        if (session == null || session.getAttribute("user") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("not logged in on " + instanceName);
        }
        return ResponseEntity.ok(session.getAttribute("user") + " on " + instanceName);
    }

    @PostMapping("/logout")
    String logout(HttpSession session) {
        session.invalidate();
        return "logged out";
    }

    @PostMapping("/cart/broken")
    String addToCart(@RequestParam String item, HttpSession session) {
        Cart cart = new Cart();
        cart.add(item);
        session.setAttribute("cart", cart);
        return "added " + item;
    }

    @PostMapping("/cart/fixed")
    String addToSerializableCart(@RequestParam String item, HttpSession session) {
        SerializableCart cart = new SerializableCart();
        cart.add(item);
        session.setAttribute("cart", cart);
        return "added " + item;
    }
}
