package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.Image
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ImageRepository : JpaRepository<Image, Long> {

    @Query("""
        SELECT i FROM Image i
        LEFT JOIN i.tags t
        WHERE (COALESCE(:requiredTagsSize, 0) = 0 OR
               (SELECT COUNT(DISTINCT rt.name) FROM Image img
                JOIN img.tags rt
                WHERE img.id = i.id AND rt.name IN :requiredTags) = :requiredTagsSize)
        AND (COALESCE(:forbiddenTagsSize, 0) = 0 OR i.id NOT IN
             (SELECT img2.id FROM Image img2 JOIN img2.tags ft WHERE ft.name IN :forbiddenTags))
        GROUP BY i.id
        ORDER BY
            CASE WHEN :optionalTagsSize > 0 THEN
                (SELECT COUNT(ot.name) FROM Image img3
                 JOIN img3.tags ot
                 WHERE img3.id = i.id AND ot.name IN :optionalTags)
            ELSE 0 END DESC,
            i.uploadDate DESC
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
