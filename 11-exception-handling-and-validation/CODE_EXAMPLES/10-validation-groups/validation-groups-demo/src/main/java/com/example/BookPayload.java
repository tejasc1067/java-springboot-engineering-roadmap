package com.example;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

// One DTO, validated differently depending on the operation, via groups.
//
//   id    -> forbidden on create (server assigns it), required on update (which row?).
//   title -> always required. It has NO groups attribute, so it belongs to the
//            built-in jakarta.validation.groups.Default group. That is the subtle part:
//            Default constraints are NOT checked when you trigger ONLY a custom group.
//            The controller therefore asks for {Default.class, OnCreate.class} (or OnUpdate),
//            so title runs on BOTH paths and id runs on only the path that tagged it.
public record BookPayload(
        @Null(groups = OnCreate.class,  message = "id must be absent on create")
        @NotNull(groups = OnUpdate.class, message = "id is required on update")
        Long id,

        @NotBlank
        String title) {
}
