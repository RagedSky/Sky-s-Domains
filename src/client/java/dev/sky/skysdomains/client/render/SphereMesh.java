package dev.sky.skysdomains.client.render;

/**
 * A unit UV sphere as a quad list: for each quad four vertices with position (x,y,z),
 * normal (= position on a unit sphere) and texture coordinates (u,v).
 */
public final class SphereMesh {
	public static final SphereMesh HIGH = new SphereMesh(20, 32);
	public static final SphereMesh LOW = new SphereMesh(10, 16);

	/** quads * 4 vertices * 5 floats (x, y, z, u, v) */
	public final float[] data;
	public final int quadCount;

	private SphereMesh(int stacks, int slices) {
		quadCount = stacks * slices;
		data = new float[quadCount * 4 * 5];
		int i = 0;
		for (int stack = 0; stack < stacks; stack++) {
			double phi0 = Math.PI * stack / stacks;
			double phi1 = Math.PI * (stack + 1) / stacks;
			for (int slice = 0; slice < slices; slice++) {
				double theta0 = 2 * Math.PI * slice / slices;
				double theta1 = 2 * Math.PI * (slice + 1) / slices;
				i = put(i, phi0, theta0, (float) slice / slices, (float) stack / stacks);
				i = put(i, phi1, theta0, (float) slice / slices, (float) (stack + 1) / stacks);
				i = put(i, phi1, theta1, (float) (slice + 1) / slices, (float) (stack + 1) / stacks);
				i = put(i, phi0, theta1, (float) (slice + 1) / slices, (float) stack / stacks);
			}
		}
	}

	private int put(int i, double phi, double theta, float u, float v) {
		data[i++] = (float) (Math.sin(phi) * Math.cos(theta));
		data[i++] = (float) Math.cos(phi);
		data[i++] = (float) (Math.sin(phi) * Math.sin(theta));
		data[i++] = u;
		data[i++] = v;
		return i;
	}
}
