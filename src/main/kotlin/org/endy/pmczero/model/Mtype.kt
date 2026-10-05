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
    FOLDER(8)

//    MFILE_UNDEFINED = 0
//    MFILE_LOCATION = 1
//    MFILE_PHOTO = 2 # Fotos und Videos selbst gedreht
//    MFILE_MOVIE = 3 # Filme aus Kino und Fernsehen
//    MFILE_BOOK = 4 # Ebooks
//    MFILE_IMEDIUM = 5
//    MFILE_BOOKMARK = 6 # Bookmarks
//    MFILE_YOUTUBE = 7 # Youtube
//    MFILE_FOLDER = 8 # Folder


    ;

    companion object {

        private val PHOTO_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "svg", "heic", "heif")
        private val MOVIE_EXTENSIONS =
            setOf("mp4", "m4v", "avi", "mkv", "mov", "wmv", "flv", "webm", "mpg", "mpeg", "3gp")
        private val BOOK_EXTENSIONS =
            setOf("epub", "pdf", "mobi", "azw", "azw3", "djvu", "cbz", "cbr")

        /**
         * The type that fits the file extension of [name], [UNDEFINED] for an extension no type
         * claims and for a name without one. So a directory listing can be turned into media
         * without asking the caller about the type of every single file.
         */
        fun of(name: String): Mtype = when (name.substringAfterLast('.', "").lowercase()) {
            in PHOTO_EXTENSIONS -> PHOTO
            in MOVIE_EXTENSIONS -> MOVIE
            in BOOK_EXTENSIONS -> BOOK
            else -> UNDEFINED
        }
    }

}
