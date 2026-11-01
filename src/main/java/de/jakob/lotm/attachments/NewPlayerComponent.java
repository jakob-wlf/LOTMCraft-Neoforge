package de.jakob.lotm.attachments;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

public class NewPlayerComponent {
    private boolean hasReceivedNewPlayerPerks = false;
    private boolean hasReceivedGuidingBook = false;
    public NewPlayerComponent() {}

    
    // Getters and setters


    public boolean isHasReceivedNewPlayerPerks() {
        return hasReceivedNewPlayerPerks;
    }

    public void setHasReceivedNewPlayerPerks(boolean hasReceivedNewPlayerPerks) {
        this.hasReceivedNewPlayerPerks = hasReceivedNewPlayerPerks;
    }

    public boolean isHasReceivedGuidingBook() {
        return hasReceivedGuidingBook;
    }

    public void setHasReceivedGuidingBook(boolean hasReceivedGuidingBook) {
        this.hasReceivedGuidingBook = hasReceivedGuidingBook;
    }

    public static final IAttachmentSerializer<CompoundTag, NewPlayerComponent> SERIALIZER =
            new IAttachmentSerializer<>() {
                @Override
                public NewPlayerComponent read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider lookup) {
                    NewPlayerComponent component = new NewPlayerComponent();
                    component.hasReceivedNewPlayerPerks = tag.getBoolean("hasReceivedNewPlayerPerks");
                    component.hasReceivedGuidingBook = tag.getBoolean("hasReceivedGuidingBook");
                    return component;
                }

                @Override
                public CompoundTag write(NewPlayerComponent component, HolderLookup.Provider lookup) {
                    CompoundTag tag = new CompoundTag();
                    tag.putBoolean("hasReceivedNewPlayerPerks", component.hasReceivedNewPlayerPerks);
                    tag.putBoolean("hasReceivedGuidingBook", component.hasReceivedGuidingBook);
                    return tag;
                }
            };
}