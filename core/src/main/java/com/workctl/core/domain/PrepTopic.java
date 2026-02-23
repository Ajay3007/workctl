package com.workctl.core.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A single study item in the Interview Prep checklist.
 * Three-level hierarchy: Category (DSA) → Section/Subtopic (Arrays) → Item (Two Pointer)
 *
 * Stored in 05_Interviews/prep-topics.md.
 * Format:
 *   ## DSA
 *   ### Arrays
 *   - [x] Two pointer technique <!-- id=uuid -->
 *   - [ ] Sliding window        <!-- id=uuid -->
 */
public class PrepTopic {

    private String  id;
    private String  category;  // top level  — e.g. "DSA", "OS", "Networking"
    private String  section;   // sub-level  — e.g. "Arrays", "Trees" (nullable = no sub-group)
    private String  name;      // item name  — e.g. "Two pointer technique"
    private boolean done;

    /** Full constructor (with section). */
    public PrepTopic(String category, String section, String name, boolean done) {
        this.id       = UUID.randomUUID().toString();
        this.category = category != null ? category.trim() : "General";
        this.section  = (section != null && !section.isBlank()) ? section.trim() : null;
        this.name     = name != null ? name.trim() : "";
        this.done     = done;
    }

    /** Convenience constructor without section (section = null). */
    public PrepTopic(String category, String name, boolean done) {
        this(category, null, name, done);
    }

    public String  getId()       { return id; }
    public String  getCategory() { return category; }
    public String  getSection()  { return section; }   // may be null
    public String  getName()     { return name; }
    public boolean isDone()      { return done; }

    public void setId(String id)         { this.id       = id; }
    public void setCategory(String c)    { this.category = c; }
    public void setSection(String s)     { this.section  = (s != null && !s.isBlank()) ? s.trim() : null; }
    public void setName(String n)        { this.name     = n; }
    public void setDone(boolean d)       { this.done     = d; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrepTopic t)) return false;
        return Objects.equals(id, t.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
