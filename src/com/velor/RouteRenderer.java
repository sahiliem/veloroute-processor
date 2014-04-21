package com.velor;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;

import com.velor.algorithms.geodata.LatLng;
import com.velor.algorithms.geodata.Projection;
import com.velor.algorithms.spatial.Point;
import com.velor.map.provider.route.RouteProvider;
import com.velor.map.provider.tile.Tile;
import com.velor.map.provider.tile.TileImpl;
import com.velor.map.provider.tile.TileProvider;
import com.velor.map.storage.tile.TileStorage;
import com.velor.map.vo.Route;

public class RouteRenderer extends AbstractPreprocessor {

	private TileProvider tileProvider;
	private RouteProvider routeProvider;
	private TileStorage tileStorate;
	private Projection projection;
	private float routeAlpha;
	private float routeWidth;
	private int tilePixels;
	private int minzoom = 9;
	private int maxzoom = 10;
	private String destination;

	public void setDestination(String destination) {
		this.destination = destination;
	}

	public void setMinzoom(int minzoom) {
		this.minzoom = minzoom;
	}

	public void setMaxzoom(int maxzoom) {
		this.maxzoom = maxzoom;
	}

	public void setTilePixels(int tilePixels) {
		this.tilePixels = tilePixels;
	}

	public void setTileProvider(TileProvider tileProvider) {
		this.tileProvider = tileProvider;
	}

	public void setRouteProvider(RouteProvider routeProvider) {
		this.routeProvider = routeProvider;
	}

	public void setTileStorate(TileStorage tileStorate) {
		this.tileStorate = tileStorate;
	}

	public void setProjection(Projection projection) {
		this.projection = projection;
	}

	public void setRouteAlpha(float routeAlpha) {
		this.routeAlpha = routeAlpha;
	}

	public void setRouteWidth(float routeWidth) {
		this.routeWidth = routeWidth;
	}

	private class PR {
		long start = 0;
		double total = 0;
		int pcent = 0;
		int current = 0;
		int pcent_ = 0;

		public PR() {
			super();
			start = new Date().getTime();
		}

		void update() {
			pcent = (int) (current / total * 100);

			if (pcent != pcent_) {
				long time = new Date().getTime();
				if (time - start > 1000) {
					System.out.println(pcent + "% done");
					start = time;
				}
			}
			pcent_ = pcent;
			current++;
		}

		void update(int n) {
			current += n;
			pcent = (int) (current / total * 100);
			if (pcent != pcent_) {
				long time = new Date().getTime();
				if (time - start > 1000) {
					System.out.println(pcent + "% done");
					start = time;
				}
			}
			pcent_ = pcent;

		}
	}

	@Override
	public void preprocess() {

		tileStorate.setTileDirectory(destination);
		System.out.println("Rendering routes on tiles");

		File f = new File(destination);
		if (f.exists()) {
			try {
				FileUtils.deleteDirectory(f);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		List<Route> list = routeProvider.getRoutes();
		PR pr = new PR();

		for (int i = minzoom; i <= maxzoom; i++) {
			pr.total += Math.pow(2, i) * list.size();
		}

		for (int zoom = minzoom; zoom <= maxzoom; zoom++) {
			System.out.println(" ... rendering for zoom " + zoom);
			int ntiles = (int) Math.pow(2, zoom);
			for (Route route : list) {
				if (route.getType().getId() >= -1) {
					render(route, zoom, pr);
				} else {
					pr.update(ntiles);
				}
			}
		}
	}

	protected void render(Route route, int zoom, PR pr) {
		int n = route.size();
		List<Point> xy = new ArrayList<>();

		float routeAlpha = this.routeAlpha;
		float routeWidth = this.routeWidth;

		if (zoom == minzoom) {
			routeAlpha /= 8;
			routeWidth /= 4;
		} else if (zoom == minzoom + 1) {
			routeAlpha /= 4;
			routeWidth /= 4;
		} else if (zoom == minzoom + 2) {
			routeAlpha /= 2;
			routeWidth /= 2;
		}

		Set<Point> tiles = new HashSet<>();
		for (int i = 0, j = 0; i < n; i++) {

			if (zoom >= route.minZoom[i] && zoom <= route.maxZoom[i]) {
				Point p = projection.toPoint(new LatLng(route.data[i]), zoom);
				xy.add(p);
				tiles.add(new Point((int) xy.get(j).x, (int) xy.get(j).y));
				j++;
			}
		}

		int w = tilePixels;
		int h = tilePixels;
		Tile tile = null;
		BufferedImage img = null;
		// int prevX = -1, prevY = -1;
		int x = -1;
		int y = -1;
		Graphics2D g = null;

		n = xy.size();
		int[] xPoints = new int[n];
		int[] yPoints = new int[n];

		int ntiles = (int) Math.pow(2, zoom);
		for (Point tileCoord : tiles) {
			x = (int) tileCoord.x;
			y = (int) tileCoord.y;
			for (int i = 0; i < n; i++) {
				xPoints[i] = (int) ((xy.get(i).x - x) * w);
				yPoints[i] = (int) ((xy.get(i).y - y) * h);
			}

			try {
				tile = tileProvider.getTile(x, y, zoom);
				img = ImageIO.read(new ByteArrayInputStream(tile.getData()));
				g = (Graphics2D) img.getGraphics();
				g.setComposite(AlphaComposite.getInstance(
						AlphaComposite.SRC_ATOP, routeAlpha));

				Color c = new Color(route.getType().getColor());
				g.setPaint(c);
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_RENDERING,
						RenderingHints.VALUE_RENDER_QUALITY);
				g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
						RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
				g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
						RenderingHints.VALUE_COLOR_RENDER_QUALITY);

				g.setStroke(new BasicStroke(routeWidth, BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));

				g.drawPolyline(xPoints, yPoints, n);

				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ImageIO.write(img, "png", bos);
				tile = new TileImpl(bos.toByteArray(), w, h);
				tileStorate.create(tile, zoom, x, y);
				bos.close();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			pr.update();
			ntiles--;
		}

		pr.update(ntiles);
	}
}