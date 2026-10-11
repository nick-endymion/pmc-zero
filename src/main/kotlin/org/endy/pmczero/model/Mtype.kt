package org.endy.pmczero.model

enum class Mtype(val i: Int) {

    UNDEFINED(0),
    LOCATION(1),
    PHOTO(2),
    MOVIE(3),
    BOOK(4),
    IMEDIUM(5),
    BOOKMARK(6),
    YOUTUBE(7),
    FOLDER(8),

    /**
     * A sound file: bytes the api serves and a browser plays rather than shows.
     *
     * Was not a type of its own until a sound file could reach this application at all, which is
     * why every one imported before this was [UNDEFINED] together with a file that has no
     * extension and nothing else about it. Nothing backfills those: the column is what it was
     * written as, and rewriting the rows of every set ever scanned is a larger thing to do than
     * to read the extension where it is on hand. So [of] decides by extension and this is the
     * answer, but a caller holding only an old row still has to fall back on the name.
     */
    AUDIO(9)

//    MFILE_UNDEFINED = 0
//    MFILE_LOCATION = 1
//    MFILE_PHOTO = 2 # Fotos und Videos selbst gedreht
//    MFILE_MOVIE = 3 # Filme aus Kino und Fernsehen
//    MFILE_BOOK = 4 # Ebooks
//    MFILE_IMEDIUM = 5
//    MFILE_BOOKMARK = 6 # Bookmarks
//    MFILE_YOUTUBE = 7 # Youtube
//    MFILE_FOLDER = 8 # Folder
//    MFILE_AUDIO = 9 # Sound


    ;

    companion object {

        private val PHOTO_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "svg", "heic", "heif")
        private val MOVIE_EXTENSIONS =
            setOf("mp4", "m4v", "avi", "mkv", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "3gp")
        private val BOOK_EXTENSIONS =
            setOf("epub", "pdf", "mobi", "azw", "azw3", "djvu", "cbz", "cbr")

        /**
         * What a browser plays out of the box, and then rather more than that.
         *
         * The first group is what every current browser decodes without a plugin, which is also
         * what [org.springframework.http.MediaTypeFactory] names as an audio content type: mp3, aac
         * in an m4a container, flac, wav and ogg. The rest are here because a file of that name is
         * still a sound file and should not be lumped in with a file that has no extension at
         * all — where this application has no player for it is a fact about the reader, not about
         * the file.
         */
        private val AUDIO_EXTENSIONS =
            setOf(
                "mp3", "aac", "m4a", "flac", "wav", "ogg", "oga", "opus",
                // sound, but needing a plugin or a converter before this browser will play it
                "wma", "aif", "aiff", "alac", "ape", "mka", "mid", "midi", "amr", "3ga"
            )

        /**
         * The type that fits the file extension of [name], [UNDEFINED] for an extension no type
         * claims and for a name without one. So a directory listing can be turned into media
         * without asking the caller about the type of every single file.
         */
        fun of(name: String): Mtype = when (name.substringAfterLast('.', "").lowercase()) {
            in PHOTO_EXTENSIONS -> PHOTO
            in MOVIE_EXTENSIONS -> MOVIE
            in BOOK_EXTENSIONS -> BOOK
            in AUDIO_EXTENSIONS -> AUDIO
            else -> UNDEFINED
        }
    }

}
