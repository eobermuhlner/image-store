package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.Image
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ImageRepository : JpaRepository<Image, Long> {

    @Query("""
        SELECT i
        FROM Image i
        WHERE
          ( :requiredTagsSize = 0 OR EXISTS (
              SELECT 1
              FROM Image i2
              JOIN i2.tags r
              WHERE i2.id = i.id
                AND r.name IN :requiredTags
              GROUP BY i2.id
              HAVING COUNT(DISTINCT r.name) = :requiredTagsSize
          ))
        AND
          ( :forbiddenTagsSize = 0 OR NOT EXISTS (
              SELECT 1 FROM i.tags f WHERE f.name IN :forbiddenTags
          ))
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
