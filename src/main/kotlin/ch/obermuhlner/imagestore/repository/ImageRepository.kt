package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.Image
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ImageRepository : JpaRepository<Image, Long> {

    @Query("""
        SELECT DISTINCT i FROM Image i
        LEFT JOIN i.tags t
        WHERE (COALESCE(:requiredTagsSize, 0) = 0 OR
               (SELECT COUNT(DISTINCT rt.name) FROM Image img
                JOIN img.tags rt
                WHERE img.id = i.id AND rt.name IN :requiredTags) = :requiredTagsSize)
        AND (COALESCE(:optionalTagsSize, 0) = 0 OR t.name IN :optionalTags)
        AND (COALESCE(:forbiddenTagsSize, 0) = 0 OR i.id NOT IN
             (SELECT img2.id FROM Image img2 JOIN img2.tags ft WHERE ft.name IN :forbiddenTags))
    """)
    fun searchByTags(
        @Param("requiredTags") requiredTags: List<String>,
        @Param("requiredTagsSize") requiredTagsSize: Int,
        @Param("optionalTags") optionalTags: List<String>,
        @Param("optionalTagsSize") optionalTagsSize: Int,
        @Param("forbiddenTags") forbiddenTags: List<String>,
        @Param("forbiddenTagsSize") forbiddenTagsSize: Int
    ): List<Image>
}
