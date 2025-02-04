package com.example.MessageBroker.utilities;

import com.example.MessageBroker.entities.event.Event;
import com.example.MessageBroker.entities.event.EventType;
import com.example.MessageBroker.entities.subscription.Subscription;
import com.example.MessageBroker.entities.user.Role;
import com.example.MessageBroker.entities.user.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseService {

    private static final String URL = "jdbc:mysql://sql7.freesqldatabase.com:3306/sql7760535?useSSL=false&serverTimezone=UTC";
    private static final String USERNAME = "sql7760535";
    private static final String PASSWORD = "SEsuNXySCk";

    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM user")) {

            while (rs.next()) {

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

    public User findByUsernameAndPassword(String username, String password) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM user WHERE username = ?" + "AND password = ?")) {

            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = new User(
                        rs.getString("username"),
                        rs.getString("password"),
                        Role.valueOf(rs.getString("role"))
                );
                user.setId(rs.getLong("id"));
                return user;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return null;
    }

    public void createEventType(EventType eventType) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO event_type (event_type_name, description) VALUES (?, ?)")) {
            stmt.setString(1, eventType.getEventTypeName());
            stmt.setString(2, eventType.getDescription());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public EventType findEventTypeById(Long id) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM event_type WHERE id = ?")) {

            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                EventType eventType = new EventType(rs.getString("event_type_name"),
                        rs.getString("description"));
                eventType.setId(rs.getLong("id"));
                return eventType;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return null;
    }

    public void createEvent(Event event) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO event (event_type, payload) VALUES (?, ?)")) {
            stmt.setLong(1, event.getEventType().getId());
            stmt.setString(2, event.getPayload());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public Long findEventTypeIdByEventTypeName(String eventTypeName) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM event_type WHERE event_type_name = ?")) {

            stmt.setString(1, eventTypeName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return null;
    }

    public List<EventType> getAllEventTypes() {
        List<EventType> eventTypeList = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM event_type")) {

            while (rs.next()) {
                EventType eventType = new EventType(rs.getString("event_type_name"), rs.getString("description"));
                eventType.setId(rs.getLong("id"));
                eventTypeList.add(eventType);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return eventTypeList;
    }

    public void createSubscription(Subscription subscription) {
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             PreparedStatement stmt = connection.prepareStatement("INSERT INTO subscription (user_id, event_id) VALUES (?, ?)")) {
            stmt.setLong(1, subscription.getUserId());
            stmt.setLong(2, subscription.getEventTypeId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public List<Long> getAllSubscribers(Long eventTypeId) {
        List<Long> userIdList = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM subscription WHERE event_id =" + eventTypeId)) {

            while (rs.next()) {
                userIdList.add(rs.getLong("user_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return userIdList;
    }


}
