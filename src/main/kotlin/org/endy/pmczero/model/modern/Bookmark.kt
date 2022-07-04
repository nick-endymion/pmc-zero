package org.endy.pmczero.model.modern

import java.sql.Date
import javax.persistence.*

@Entity
@Table(name = "a.bookmark")
class Bookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Int? = null

    @Column(name = "name", nullable = true)
    var name: String? = null

    @Column(name = "url", nullable = true)
    var url: String? = null

    @Column(name = "created_at", nullable = true,columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP" )
    var created_at: Date? = null

    @Column(name = "updated_at", nullable = true,columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" )
    var updated_at: Date? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medium_id", nullable = true)
    var medium: Medium? = null

}

