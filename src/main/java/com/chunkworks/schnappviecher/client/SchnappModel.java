/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.client;

import com.chunkworks.schnappviecher.Schnappviech;
import com.chunkworks.schnappviecher.domain.Encounter;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Authored Wudele: a horned, shaggy snapping head on a sackcloth costume.
 * AF: bones represent the cloak, feet, head and hinged lower jaw. RI: private
 * parts belong to one baked root and are reset before each pose; no game state
 * is changed by rendering. Vanilla units are 1/16 block; full height is 56 units.
 */
public final class SchnappModel extends HierarchicalModel<Schnappviech> {
    private final ModelPart root,head,jaw,cloak,leftFoot,rightFoot;
    private final ModelPart[] parts;
    /** requires: our baked layer; effects: takes ownership of its model parts; throws: missing bone exception. */
    public SchnappModel(ModelPart root) {
        this.root=root;head=root.getChild("head");jaw=head.getChild("jaw");cloak=root.getChild("cloak");
        parts=root.getAllParts().toArray(ModelPart[]::new);
        leftFoot=root.getChild("left_foot");rightFoot=root.getChild("right_foot");
    }
    /** requires: none; effects: creates the authored mesh; throws: none. */
    public static LayerDefinition layer() {
        MeshDefinition mesh=new MeshDefinition();PartDefinition root=mesh.getRoot();
        PartDefinition cloak=root.addOrReplaceChild("cloak",CubeListBuilder.create().texOffs(0,0)
                .addBox(-7,-19,-5.5f,14,39,11),PartPose.ZERO);
        // Each long panel hangs from the neck and flares very slightly at the hem.
        // Their folds overlap throughout; there are no horizontal tier seams.
        for(int side:new int[]{-1,1}) {
            cloak.addOrReplaceChild("side_panel_"+side,CubeListBuilder.create().texOffs(0,0)
                    .addBox(-.6f,0,-6,1.2f,39,12),PartPose.offsetAndRotation(side*6.8f,-19,0,0,0,-side*.052f));
        }
        for(int i=0;i<6;i++) {
            float x=-7+i*2.5f;
            cloak.addOrReplaceChild("front_fold_"+i,CubeListBuilder.create().texOffs(8+i*3,0)
                    .addBox(-1.1f,0,-.6f,2.2f,39,.8f),PartPose.offsetAndRotation(x,-19,-5.6f,-.04f,0,(i-2.5f)*-.009f));
            cloak.addOrReplaceChild("back_fold_"+i,CubeListBuilder.create().texOffs(8+i*3,0)
                    .addBox(-1.1f,0,-.4f,2.2f,39,.8f),PartPose.offsetAndRotation(x,-19,5.6f,.04f,0,(i-2.5f)*-.009f));
        }
        // Overlapping, uneven strips make an actual hanging hem, rather than a solid cuboid skirt.
        for(int i=0;i<7;i++) {
            float x=-8+i*2.3f;
            cloak.addOrReplaceChild("front_fringe_"+i,CubeListBuilder.create().texOffs(12+i*3,15)
                    .addBox(0,0,0,2.5f,5+(i%3),.6f),PartPose.offsetAndRotation(x,16,-7.2f,-.04f,0,(i%2==0?.025f:-.025f)));
            cloak.addOrReplaceChild("back_fringe_"+i,CubeListBuilder.create().texOffs(12+i*3,15)
                    .addBox(0,0,0,2.5f,4+(i%3),.6f),PartPose.offset(x,16,6.8f));
        }
        root.addOrReplaceChild("left_foot",CubeListBuilder.create().texOffs(80,64).addBox(-2,0,-4,4,4,7),PartPose.offset(4,20,0));
        root.addOrReplaceChild("right_foot",CubeListBuilder.create().texOffs(80,64).addBox(-2,0,-4,4,4,7),PartPose.offset(-4,20,0));
        PartDefinition head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,64)
                .addBox(-7.5f,-10,-8,15,12,15).texOffs(0,64).addBox(-6,-6,-23,12,7,17)
                .texOffs(80,64).addBox(-6.5f,-5.5f,-24,13,4,2),PartPose.offset(0,-15,0));
        head.addOrReplaceChild("jaw",CubeListBuilder.create().texOffs(0,64).addBox(-6,-1,-17,12,3,19)
                .texOffs(96,16).addBox(-5,-1.2f,-16,10,.5f,16),PartPose.offset(0,3,-6));
        PartDefinition jaw=head.getChild("jaw");
        for(int i=0;i<5;i++) {
            float z=-20+i*3;
            for(int side:new int[]{-1,1}) {
                head.addOrReplaceChild("upper_tooth_"+side+"_"+i,CubeListBuilder.create().texOffs(96,32)
                        .addBox(-.8f,0,-.8f,1.6f,2.5f+(i%2),1.6f),PartPose.offset(side*4.8f,.6f,z));
                jaw.addOrReplaceChild("lower_tooth_"+side+"_"+i,CubeListBuilder.create().texOffs(96,32)
                        .addBox(-.7f,-2,-.7f,1.4f,2,1.4f),PartPose.offset(side*4.7f,-.8f,z+6));
            }
        }
        for(int side:new int[]{-1,1}) {
            head.addOrReplaceChild("eye_"+side,CubeListBuilder.create().texOffs(112,48).addBox(-1.8f,-1.8f,-.4f,3.6f,3.6f,.8f)
                    .texOffs(112,56).addBox(-.6f,-.8f,-.9f,1.2f,1.8f,.8f),PartPose.offsetAndRotation(side*6,-6,-8.2f,0,side*-.4f,side*-.15f));
            PartDefinition horn=head.addOrReplaceChild("horn_"+side,CubeListBuilder.create().texOffs(96,32)
                    .addBox(-1.8f,-6,-1.8f,3.6f,6,3.6f),PartPose.offsetAndRotation(side*5,-9,2,0,0,side*.3f));
            PartDefinition bend=horn.addOrReplaceChild("bend",CubeListBuilder.create().texOffs(96,32)
                    .addBox(-1.2f,-4,-1.2f,2.4f,4,2.4f),PartPose.offsetAndRotation(0,-5,0,.4f,0,side*-.45f));
            bend.addOrReplaceChild("tip",CubeListBuilder.create().texOffs(80,64).addBox(-.6f,-3,-.6f,1.2f,3,1.2f),PartPose.offsetAndRotation(0,-3,0,.5f,0,side*-.5f));
            for(int i=0;i<6;i++)head.addOrReplaceChild("ruff_"+side+"_"+i,CubeListBuilder.create().texOffs(0,64)
                    .addBox(-1.5f,-1,-1.5f,3,6+(i%2)*2,3),PartPose.offsetAndRotation(side*7,-4+i%3,-5+i*2,0,0,side*-.15f));
        }
        for(int i=0;i<5;i++)head.addOrReplaceChild("brow_"+i,CubeListBuilder.create().texOffs(0,64)
                .addBox(-1.5f,-2,-2,3,4,4),PartPose.offsetAndRotation(-6+i*3,-9,-7,.2f,0,(i-2)*.07f));
        return LayerDefinition.create(mesh,128,128);
    }
    @Override public ModelPart root(){return root;}
    /** requires: a render pose stack; effects: moves it to the mouth for the held item layer; throws: none. */
    public void mouth(com.mojang.blaze3d.vertex.PoseStack pose) { head.translateAndRotate(pose);pose.translate(0,.19,-1.28); }
    @Override public void setupAnim(Schnappviech e,float swing,float amount,float age,float yaw,float pitch) {
        for(ModelPart part:parts)part.resetPose();
        float walk=Mth.sin(swing*.65f)*Math.min(amount,1);
        leftFoot.xRot=walk*.55f;rightFoot.xRot=-walk*.55f;
        cloak.zRot=walk*.035f;
        head.yRot=yaw*Mth.DEG_TO_RAD*.65f;head.xRot=pitch*Mth.DEG_TO_RAD*.5f;
        head.zRot=e.watched()?-.16f:Mth.sin(age*.05f)*.035f;
        head.y+=Mth.sin(age*.07f)*.22f+Math.abs(walk)*.35f;
        jaw.xRot=e.hasHeldItem()?.32f:.14f;
        if(e.jawTicks()>0)jaw.xRot=.15f+Math.abs(Mth.sin(age*.8f))*.5f;
        if(e.stage()==Encounter.Stage.RANSOM){head.xRot+=.15f;cloak.xRot=-.035f;}
        if(e.stage()==Encounter.Stage.RETREAT){head.xRot+=.25f;head.zRot=Mth.sin(age*.3f)*.09f;}
        if(e.reactionTicks()>0){head.xRot-=Mth.sin(e.reactionTicks()*.3f)*.35f;jaw.xRot=.7f;}
    }
}
