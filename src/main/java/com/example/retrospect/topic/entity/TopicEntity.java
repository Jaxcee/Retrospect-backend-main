package com.example.retrospect.topic.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class TopicEntity{
    @Id
    @GeneratedValue
    private Long topicId;
    private String roomId;
    private String topicName;


}
