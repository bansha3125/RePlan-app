package com.replan.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.replan.api.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}