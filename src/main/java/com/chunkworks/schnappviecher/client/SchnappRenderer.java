/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.client;
import com.chunkworks.schnappviecher.Content;
import com.chunkworks.schnappviecher.Schnappviech;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/** Creature renderer and actual ItemStack in its jaws; holds only baked render resources. */
public final class SchnappRenderer extends MobRenderer<Schnappviech,SchnappModel> {
    private static final ResourceLocation TEXTURE=Content.id("textures/entity/schnappviech.png");
    /** requires: client renderer context; effects: bakes a model and installs its mouth layer; throws: none. */
    public SchnappRenderer(EntityRendererProvider.Context context) {
        super(context,new SchnappModel(SchnappModel.layer().bakeRoot()),.75f);
        addLayer(new RenderLayer<Schnappviech,SchnappModel>(this) {
            @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,Schnappviech entity,
                    float swing,float amount,float partial,float age,float yaw,float pitch) {
                var item=entity.heldItem();if(item.isEmpty())return;
                pose.pushPose();getParentModel().mouth(pose);pose.scale(.8f,.8f,.8f);pose.mulPose(Axis.ZP.rotationDegrees(18));
                context.getItemInHandRenderer().renderItem(entity,item,ItemDisplayContext.FIXED,false,pose,buffers,light);
                pose.popPose();
            }
        });
    }
    @Override public ResourceLocation getTextureLocation(Schnappviech entity){return TEXTURE;}
}
