package com.valhallagame.characterserviceserver.repository;

import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.valhallagame.characterserviceserver.model.Character;

public interface CharacterRepository extends JpaRepository<Character, Integer> {
	public Optional<Character> findByCharacterName(String characterName);

	public List<Character> findByOwnerUsername(String ownerUsername);

	@Transactional
	@Modifying
	@Query(value = "INSERT INTO selected_character (owner, character_name) " + " VALUES (:owner, :character_name)"
			+ "ON CONFLICT (owner) DO UPDATE SET character_name = :character_name", nativeQuery = true)
	public void setSelectedCharacter(@Param("owner") String owner, @Param("character_name") String characterName);

	@Query(value = "SELECT c.* from character c join selected_character sc USING (owner, character_name) where sc.owner = :owner", nativeQuery = true)
	public Optional<Character> getSelectedCharacter(@Param("owner") String owner);
}
