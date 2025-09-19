"""
SIF Container Caching Module

This module provides caching functionality for SIF containers to avoid
unnecessary rebuilds and improve CLI performance.
"""

import os
import hashlib
import json
import time
from pathlib import Path
from typing import Dict, Optional, Tuple
import logging

logger = logging.getLogger(__name__)

class SIFCache:
    """Cache manager for SIF containers."""
    
    def __init__(self, cache_dir: str = None):
        """Initialize the SIF cache.
        
        Args:
            cache_dir: Directory to store cache metadata (default: ~/.rtdp/sif_cache)
        """
        if cache_dir is None:
            cache_dir = os.path.expanduser("~/.rtdp/sif_cache")
        
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.metadata_file = self.cache_dir / "metadata.json"
        self.metadata = self._load_metadata()
    
    def _load_metadata(self) -> Dict:
        """Load cache metadata from file."""
        if self.metadata_file.exists():
            try:
                with open(self.metadata_file, 'r') as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError) as e:
                logger.warning(f"Could not load cache metadata: {e}")
        return {}
    
    def _save_metadata(self):
        """Save cache metadata to file."""
        try:
            with open(self.metadata_file, 'w') as f:
                json.dump(self.metadata, f, indent=2)
        except IOError as e:
            logger.warning(f"Could not save cache metadata: {e}")
    
    def _calculate_file_hash(self, file_path: str) -> str:
        """Calculate SHA256 hash of a file."""
        hash_sha256 = hashlib.sha256()
        try:
            with open(file_path, "rb") as f:
                for chunk in iter(lambda: f.read(4096), b""):
                    hash_sha256.update(chunk)
            return hash_sha256.hexdigest()
        except IOError:
            return ""
    
    def _get_docker_image_hash(self, docker_image: str) -> str:
        """Get a hash representing the Docker image (using image name and tag)."""
        # For now, use the image name as a simple hash
        # In a more sophisticated implementation, you could query Docker registry
        return hashlib.sha256(docker_image.encode()).hexdigest()
    
    def is_sif_valid(self, sif_path: str, docker_image: str) -> bool:
        """Check if a SIF file is valid and up-to-date.
        
        Args:
            sif_path: Path to the SIF file
            docker_image: Docker image used to build the SIF
            
        Returns:
            bool: True if SIF is valid and up-to-date
        """
        if not os.path.exists(sif_path):
            return False
        
        # Get current file hash
        current_hash = self._calculate_file_hash(sif_path)
        if not current_hash:
            return False
        
        # Get expected hash from cache
        expected_hash = self.metadata.get(sif_path, {}).get('hash')
        expected_docker_hash = self.metadata.get(sif_path, {}).get('docker_hash')
        
        if not expected_hash or not expected_docker_hash:
            return False
        
        # Check if file hash matches and Docker image hasn't changed
        docker_hash = self._get_docker_image_hash(docker_image)
        return current_hash == expected_hash and docker_hash == expected_docker_hash
    
    def update_cache(self, sif_path: str, docker_image: str):
        """Update cache metadata for a SIF file.
        
        Args:
            sif_path: Path to the SIF file
            docker_image: Docker image used to build the SIF
        """
        if not os.path.exists(sif_path):
            return
        
        file_hash = self._calculate_file_hash(sif_path)
        docker_hash = self._get_docker_image_hash(docker_image)
        
        if file_hash:
            self.metadata[sif_path] = {
                'hash': file_hash,
                'docker_hash': docker_hash,
                'timestamp': time.time(),
                'size': os.path.getsize(sif_path)
            }
            self._save_metadata()
    
    def get_cache_info(self, sif_path: str) -> Optional[Dict]:
        """Get cache information for a SIF file.
        
        Args:
            sif_path: Path to the SIF file
            
        Returns:
            Dict with cache info or None if not cached
        """
        return self.metadata.get(sif_path)
    
    def clear_cache(self, sif_path: str = None):
        """Clear cache for a specific SIF or all SIFs.
        
        Args:
            sif_path: Path to specific SIF file (None for all)
        """
        if sif_path:
            if sif_path in self.metadata:
                del self.metadata[sif_path]
                self._save_metadata()
        else:
            self.metadata.clear()
            self._save_metadata()
    
    def get_cache_stats(self) -> Dict:
        """Get cache statistics.
        
        Returns:
            Dict with cache statistics
        """
        total_size = sum(info.get('size', 0) for info in self.metadata.values())
        return {
            'total_files': len(self.metadata),
            'total_size_bytes': total_size,
            'total_size_mb': total_size / (1024 * 1024),
            'cache_dir': str(self.cache_dir)
        } 