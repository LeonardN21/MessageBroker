package com.example.MessageBroker.entities.user;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserService {

    private static final String URL = "jdbc:mysql://sql7.freesqldatabase.com:3306/sql7760535?useSSL=false&serverTimezone=UTC";
    private static final String USERNAME = "sql7760535";
    private static final String PASSWORD = "SEsuNXySCk";

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM user")) {

            while (rs.next()) {
                users.add(new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        Role.valueOf(rs.getString("role"))
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return users;
    }

    public void saveUser(User user) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO user (username, password, role) VALUES (?, ?, ?)")) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getRole().name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public User findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM user WHERE username = ?")) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new User(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        Role.valueOf(rs.getString("role"))
                );
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return null;
    }
}
