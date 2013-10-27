/*****************************************************************************
 * MediaList.java
 *****************************************************************************
 * Copyright © 2013 VLC authors and VideoLAN
 * Copyright © 2013 Edward Wang
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.libvlc;

import java.util.ArrayList;

import org.videolan.vlc.Media;

import android.os.Bundle;

/**
 * Java/JNI wrapper for the libvlc_media_list_t structure.
 */
public class MediaList {
    private static final String TAG = "VLC/LibVLC/MediaList";

    /* Since the libvlc_media_t is not created until the media plays, we have
     * to cache them here. */
    private class MediaHolder {
        Media m;
        boolean noVideo; // default false
        boolean noOmx; // default false

        public MediaHolder(Media media) {
            m = media; noVideo = false; noOmx = false;
        }
        public MediaHolder(Media m_, boolean noVideo_, boolean noOmx_) {
            m = m_; noVideo = noVideo_; noOmx = noOmx_;
        }
    }

    /* TODO: add locking */
    private ArrayList<MediaHolder> mInternalList;
    private LibVLC mLibVLC; // Used to create new objects that require a libvlc instance
    private EventHandler mEventHandler;

    public MediaList(LibVLC libVLC) {
        mEventHandler = new EventHandler(); // used in init() below to fire events at the correct targets
        mInternalList = new ArrayList<MediaHolder>();
        mLibVLC = libVLC;
    }

    public void add(String mrl) {
        add(new Media(mrl, false));
    }
    public void add(Media media) {
        add(media, false, false);
    }
    public void add(Media media, boolean noVideo) {
        add(media, noVideo, false);
    }
    public void add(Media media, boolean noVideo, boolean noOmx) {
        mInternalList.add(new MediaHolder(media, noVideo, noOmx));
        signal_list_event(EventHandler.MediaListItemAdded, mInternalList.size() - 1, media.getLocation());
    }

    /**
     * Clear the media list. (remove all media)
     */
    public void clear() {
        // Signal to observers of media being deleted.
        for(int i = 0; i < mInternalList.size(); i++) {
            signal_list_event(EventHandler.MediaListItemDeleted, i, mInternalList.get(i).m.getLocation());
        }
        mInternalList.clear();
    }

    /**
     * This function checks the currently playing media for subitems at the given
     * position, and if any exist, it will expand them at the same position
     * and replace the current media.
     *
     * @param position The position to expand
     * @return -1 if no subitems were found, 0 if subitems were expanded
     */
    public int expandMedia(int position) {
        ArrayList<String> children = new ArrayList<String>();
        int ret = expandMedia(mLibVLC, position, children);
        if(ret == 0) {
            this.remove(position);
            for(String mrl : children) {
                this.insert(position, mrl);
            }
        }
        return ret;
    }
    private native int expandMedia(LibVLC libvlc_instance, int position, ArrayList<String> children);

    public void loadPlaylist(String mrl) {
        ArrayList<String> items = new ArrayList<String>();
        loadPlaylist(mLibVLC, mrl, items);
        this.clear();
        for(String item : items) {
            this.add(item);
        }
    }
    private native void loadPlaylist(LibVLC libvlc_instance, String mrl, ArrayList<String> items);

    public void insert(int position, String mrl) {
        insert(position, new Media(mrl, false));
    }
    public void insert(int position, Media media) {
        mInternalList.add(position, new MediaHolder(media));
        signal_list_event(EventHandler.MediaListItemAdded, position, media.getLocation());
    }

    public void remove(int position) {
        String uri = mInternalList.get(position).m.getLocation();
        mInternalList.remove(position);
        signal_list_event(EventHandler.MediaListItemDeleted, position, uri);
    }

    public int size() {
        return mInternalList.size();
    }

    public Media getMedia(int position) {
        return mInternalList.get(position).m;
    }

    /**
     * @param position The index of the media in the list
     * @return null if not found
     */
    public String getMRL(int position) {
        return mInternalList.get(position).m.getLocation();
    }

    public EventHandler getEventHandler() {
        return mEventHandler;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LibVLC Media List: {");
        for(int i = 0; i < size(); i++) {
            sb.append(((Integer)i).toString());
            sb.append(": ");
            sb.append(getMRL(i));
            sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    private void signal_list_event(int event, int position, String uri) {
        Bundle b = new Bundle();
        b.putString("item_uri", uri);
        b.putInt("item_index", position);
        mEventHandler.callback(event, b);
    }
}
